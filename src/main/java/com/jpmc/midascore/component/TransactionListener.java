package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class TransactionListener {
    private static final Logger logger = LoggerFactory.getLogger(TransactionListener.class);
    private final DatabaseConduit databaseConduit;
    private final RestTemplate restTemplate;

    // Inject both the DatabaseConduit and the RestTemplate
    public TransactionListener(DatabaseConduit databaseConduit, RestTemplate restTemplate) {
        this.databaseConduit = databaseConduit;
        this.restTemplate = restTemplate;
    }

    @KafkaListener(topics = "${general.kafka-topic}", groupId = "midas-core")
    public void listen(Transaction transaction) {
        UserRecord sender = databaseConduit.findById(transaction.getSenderId());
        UserRecord recipient = databaseConduit.findById(transaction.getRecipientId());

        // Step 1: Validate the transaction
        if (sender != null && recipient != null && sender.getBalance() >= transaction.getAmount()) {

            // Step 2: Call the External Incentive API
            String url = "http://localhost:8080/incentive";
            Incentive incentiveResponse = restTemplate.postForObject(url, transaction, Incentive.class);
            float incentiveAmount = (incentiveResponse != null) ? incentiveResponse.getAmount() : 0f;

            // Step 3: Adjust balances
            // Deduct original amount from sender
            sender.setBalance(sender.getBalance() - transaction.getAmount());
            // Add original amount + bonus incentive to recipient
            recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

            // Step 4: Persist changes to Database
            databaseConduit.save(sender);
            databaseConduit.save(recipient);

            // Record the transaction including the incentive field
            databaseConduit.saveTransaction(new TransactionRecord(sender, recipient, transaction.getAmount(), incentiveAmount));

            // Step 5: Log for Task 4 (Targeting Wilbur)
            if (sender.getName().equals("wilbur") || recipient.getName().equals("wilbur")) {
                logger.info("Wilbur's current balance is: {}",
                        sender.getName().equals("wilbur") ? sender.getBalance() : recipient.getBalance());
            }
        }
    }
}