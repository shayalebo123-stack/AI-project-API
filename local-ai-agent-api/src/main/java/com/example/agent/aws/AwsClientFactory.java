package com.example.agent.aws;

import com.example.agent.config.AppConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

/**
 * Builds AWS clients. When AWS_ENDPOINT_URL is set (LocalStack) the clients are pointed at it
 * and use dummy credentials, so no AWS account or environment variables are needed locally.
 * Without a custom endpoint the default AWS credential/region chain applies.
 */
public final class AwsClientFactory {

    private static final StaticCredentialsProvider LOCAL_CREDENTIALS =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));

    private AwsClientFactory() {
    }

    public static DynamoDbClient dynamoDb(AppConfig config) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(config.awsRegion()));
        if (config.awsEndpoint() != null) {
            builder.endpointOverride(URI.create(config.awsEndpoint()))
                    .credentialsProvider(LOCAL_CREDENTIALS);
        }
        return builder.build();
    }

    public static SqsClient sqs(AppConfig config) {
        SqsClientBuilder builder = SqsClient.builder().region(Region.of(config.awsRegion()));
        if (config.awsEndpoint() != null) {
            builder.endpointOverride(URI.create(config.awsEndpoint()))
                    .credentialsProvider(LOCAL_CREDENTIALS);
        }
        return builder.build();
    }
}
