//import software.amazon.awssdk.core.sync.RequestBody;
//import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
//import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.*;
//import software.amazon.awssdk.services.s3.S3Client;
//import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

import com.amazonaws.util.EC2MetadataUtils;

public class AWSExecutorW {
        //private static S3Client s3Client;
        private static SqsClient sqsClient;
        private static Ec2Client ec2Client;

        public static Region region1 = Region.US_WEST_2;
        public static Region region2 = Region.US_EAST_1;

        static final String TO_MANAGER_QUEUE_NAME = "ToManagerQueue"; 
        static final String FROM_MANAGER_QUEUE_NAME = "FromManagerQueue"; 

        static final String TERMINATION_MESSAGE = "Termination and distruction";

        static final String delimiter = "###"; 

        static final String BUCKET_JAR_NAME = "sarcasm-jar-bucket";

        private final static Filter RUNNING_INSTANCE_FILTER = Filter.builder()
                        .name("instance-state-name")
                        .values("running")
                        .build();

        // Manager-and-Worker-only variables:

        static final String TO_WORKERS_QUEUE_NAME = "ToWorkerQueue";
        static final String FROM_WORKER_QUEUE_NAME = "FromWorkerQueue";

        // Local-App-and-Worker-only variables:

        private static final String MANAGER_TAG = "Manager";

        private static final Filter MANAGER_TAG_FILTER = Filter.builder()
                .name("tag:Type")
                .values(MANAGER_TAG)
                .build();

        // Worker-only variables:

        static final int VISIBILITY_TIMEOUT = 4*60; // In seconds. 30 is the deafult. 43200 is the maximum value. 

        private static final AWSExecutorW instance = new AWSExecutorW();

        private AWSExecutorW() {
                //s3Client = S3Client.builder().region(region1).build();
                sqsClient = SqsClient.builder().region(region1).build();
                ec2Client = Ec2Client.builder().region(region2).build();
        }

        // All classes methods:

        public static AWSExecutorW getInstance() {
                return instance;
        }

        private static String getQueueUrl(String name) {
                GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                        .queueName(name)
                        .build();
                return sqsClient.getQueueUrl(getQueueRequest).queueUrl();
        }

        public static void sendMessageToQueue(String queueName, String message) {
                try{
                        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                                .queueUrl(getQueueUrl(queueName))
                                .messageBody(message)
                                .build();
                        sqsClient.sendMessage(sendMessageRequest);
                        System.err.println("Message from LocalApp sent to " + queueName + " queue: " + message);
                }catch (SqsException e){
                        System.err.println("[DEBUG]: Error tring to send message to queue " + queueName + ", Error Message: " + e.awsErrorDetails().errorMessage());
                }
        }

        public static void deleteMessageFromQueue(String queueName, Message message) {
                try{
                        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                                .queueUrl(getQueueUrl(queueName))
                                .receiptHandle(message.receiptHandle())
                                .build();
                        sqsClient.deleteMessage(deleteRequest);
                }catch (SqsException e){
                        System.err.println("[DEBUG]: Error tring to delete message from queue " + queueName + ", Error Message: " + e.awsErrorDetails().errorMessage());
                }
        }

        // Manager-and-Worker-only methods:

        public static void terminateMyself() {
                // Get the instance ID from instance metadata
                String instanceId = EC2MetadataUtils.getInstanceId();

                // Termination request
                TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                        .instanceIds(instanceId)
                        .build();

                try {
                        ec2Client.terminateInstances(terminateRequest);
                } catch (Ec2Exception e) {
                        System.err.println("[DEBUG] Error trying to terminate self: " + e.getMessage());
                }
        }

        // Worker-only methods:

        public static boolean isManagerRunning(){
                // LocalApp first wait some time to see if there is a manager initializing.
                // Create a request to describe instances with the specified tag:
                DescribeInstancesRequest describeRequest = DescribeInstancesRequest.builder()
                        .filters(MANAGER_TAG_FILTER, RUNNING_INSTANCE_FILTER)
                        .build();

                // Decribe instances and process the response:
                DescribeInstancesResponse describeResponse = ec2Client.describeInstances(describeRequest);

                //  Check if there are instances with the specified tag:
                List<Reservation>  list = describeResponse.reservations();
                if(!list.isEmpty()){
                        System.out.println("There is a manager running");
                        return true;
                }
                else
                        return false;
        }

        /**
         * @param queueName
         * @return Extract up to ONE message from the queue with visibility timeout
         */
        public static List<Message> receiveMessagesFromQueueWithVT(String queueName) {
                try{
                        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                                .queueUrl(getQueueUrl(queueName))
                                .waitTimeSeconds(20) // 0 is short polling, 1-20 is long polling
                                .visibilityTimeout(VISIBILITY_TIMEOUT) // The message will be hidden for x seconds
                                .maxNumberOfMessages(1) // Receive only one message
                                .build();
                        return sqsClient.receiveMessage(receiveRequest).messages();
                }catch (SqsException e){
                        System.err.println("[DEBUG]: Error tring to receive message from queue " + queueName + ", Error Message: " + e.awsErrorDetails().errorMessage());
                        return null;
                }
        }
   
}
