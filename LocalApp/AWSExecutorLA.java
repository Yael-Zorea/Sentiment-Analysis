import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.File;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

public class AWSExecutorLA {
        private static S3Client s3Client;
        private static SqsClient sqsClient;
        private static Ec2Client ec2Client;

        public static Region region1 = Region.US_WEST_2;
        public static Region region2 = Region.US_EAST_1;

        static final String delimiter = "###"; 

        static final String BUCKET_JAR_NAME = "sarcasm-jar-bucket";

        private static final Filter RUNNING_INSTANCE_FILTER = Filter.builder()
                .name("instance-state-name")
                .values("running")
                .build();

        // Manager-and-Local-Application-only variables:

        private final static String ami = "ami-00e95a9222311e8ed";

        static final String managerJarName = "ManagerJar.jar";
        static final String workerJarName = "WorkerJar.jar";

        static final String TO_MANAGER_QUEUE_NAME = "ToManagerQueue"; 
        static final String FROM_MANAGER_QUEUE_NAME = "FromManagerQueue"; 

        static final String TERMINATION_MESSAGE = "Termination and distruction";

        // Local-App-and-Worker-only variables:

        private static final String MANAGER_TAG = "Manager";

        private static final Filter MANAGER_TAG_FILTER = Filter.builder()
                .name("tag:Type")
                .values(MANAGER_TAG)
                .build();

        // Local-App-only variables:

        static final String managerJarPath = "C:/Users/יעל/מבוזרות/Sarcasm/Manager/target/ManagerJar.jar";
        static final String workerJarPath = "C:/Users/יעל/מבוזרות/Sarcasm/Worker/target/WorkerJar.jar";

        static final String managerScript = "#!/bin/bash\n" + 
                                "sudo yum install -y java-1.8.0-openjdk\n" + // Install OpenJDK 8
                                "aws s3 cp s3://" + BUCKET_JAR_NAME + "/" + managerJarName + " /home/ec2-user/" + managerJarName + "\n"+ // Copy the jar from an S3 bucket to the local /home/ec2-user/ directory TODO: so it will be ok if a LA deletes the jar and it's bucket? do i need to remove the jar from the home...?
                                "java -jar /home/ec2-user/"+ managerJarName +"\n";

        private static final InstanceType managerType = InstanceType.T2_SMALL; 

        private static final AWSExecutorLA instance = new AWSExecutorLA();

        private AWSExecutorLA() {
                s3Client = S3Client.builder().region(region1).build();
                sqsClient = SqsClient.builder().region(region1).build();
                ec2Client = Ec2Client.builder().region(region2).build();
        }

        // All classes methods:
    
        public static AWSExecutorLA getInstance() {
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

        // Manager-and-Local-Application-only methods:

        public static void createBucketIfNotExists(String bucketName) {
                try {
                        s3Client.createBucket(CreateBucketRequest
                                .builder()
                                .bucket(bucketName)
                                .createBucketConfiguration(
                                        CreateBucketConfiguration.builder()
                                                .locationConstraint(BucketLocationConstraint.US_WEST_2)
                                                .build())
                                .build());
                        s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                                .bucket(bucketName)
                                .build());
                        System.out.println("[DEBUG] Bucket "  + bucketName + " created successfully!");
                } catch (S3Exception e) {
                        System.out.println(e.getMessage()); // If the bucket already exists, it will print an error message
                }
        }

        public static void createEC2(String script, String tagName, InstanceType insType,  int numberOfInstances) {
                // Create RunInstancesRequest
                RunInstancesRequest runRequest = (RunInstancesRequest) RunInstancesRequest.builder()
                        .instanceType(insType) 
                        .imageId(ami)
                        .maxCount(numberOfInstances)
                        .minCount(1)
                        .keyName("vockey")
                        .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                        .userData(Base64.getEncoder().encodeToString((script).getBytes()))
                        .build();

                // Send the request to launch the instance
                RunInstancesResponse response = ec2Client.runInstances(runRequest); 

                // Tagging the instances:
                List<Instance> instances = response.instances();

                for(Instance instance : instances) {
                        String instanceId = instance.instanceId();

                        // Create a tag for the instance
                        software.amazon.awssdk.services.ec2.model.Tag tag = Tag.builder()
                                .key("Type")
                                .value(tagName)
                                .build();
        
                        // Create a tag request
                        CreateTagsRequest tagRequest = (CreateTagsRequest) CreateTagsRequest.builder() 
                                .resources(instanceId)
                                .tags(tag)
                                .build();
        
                        try {
                                ec2Client.createTags(tagRequest); // Tag the instance
                                System.out.printf(
                                        "[DEBUG] Successfully started EC2 instance %s based on AMI" + tagName + "%s\n",
                                        instanceId, ami);
        
                        } catch (Ec2Exception e) {
                                System.err.println("[ERROR] " + e.getMessage());
                                System.exit(1);
                        }   
                }

        }

        /**
         * If there already exists SQS queueu with the name queueName,
         * it will not create a new one, and won't override the existing one.
         * @param queueName
         */
        public static void createSqsQueue(String queueName) {
                try{
                        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                                .queueName(queueName)
                                .build();
                        sqsClient.createQueue(createQueueRequest);
                        System.out.println("Queue " + queueName + " created successfully!");
                }catch (SqsException e){
                        System.out.println(e.getMessage()); // If the queue already exists, it will print an error message
                }
        }

        public static List<Message> receiveMessagesFromQueue(String queueName) {
                try{
                        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                                .queueUrl(getQueueUrl(queueName))
                                .waitTimeSeconds(20) // 0 is short polling, 1-20 is long polling
                                .build();
                        return sqsClient.receiveMessage(receiveRequest).messages();
                }catch (SqsException e){
                        System.err.println("[DEBUG]: Error tring to receive messages from queue " + queueName + ", Error Message: " + e.awsErrorDetails().errorMessage());
                        return null;
                }
        }

        /**
         * If there is no queue with the name queueName, it will catch the exception.
         * @param name queue name
         */
        public static void deleteQueue(String name) {
                try{
                        DeleteQueueRequest request = DeleteQueueRequest.builder()
                        .queueUrl(getQueueUrl(name))
                        .build();
                        sqsClient.deleteQueue(request);
                }
                catch (QueueDoesNotExistException e) {
                        System.out.println("[DEGUB]: Error in queue " + name + " :" + e.getMessage());
                }
        }

        public static void uploadFileToS3(String bucketName, String key, String filePath) {
                try {
                        s3Client.putObject(
                                PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                                RequestBody.fromFile(new File(filePath)));
                        System.out.println("File " + filePath + " uploaded successfully to bucket " + bucketName + "!");
                } catch (S3Exception e) {
                    System.err.println("[DEBUG]: Error uploading file " + filePath + " to S3: " + e.getMessage());
                }
        }

        public static InputStream downloadFromS3(String bucketName, String key) { 
                try{
                        return s3Client.getObject(
                        GetObjectRequest.builder().bucket(bucketName).key(key).build(),
                        ResponseTransformer.toBytes()).asInputStream();
                }catch (S3Exception e){
                        System.err.println("[DEBUG]: Error downloading file " + key + " from bucet " + bucketName + ": " + e.getMessage());
                        return null;
                }
        }
        
        public static boolean isFileExistsInBucket(String bucketName, String fileKey) {
                try {
                    s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(fileKey).build());
                    return true;
                } catch (S3Exception e) {
                    return false;
                }
        }

        /**
         * Note: bucket MUST be empty to be deleted.
         * @param bucket
         */
        public static void deleteS3Bucket(String bucket) {
                try{
                        DeleteBucketRequest request = DeleteBucketRequest.builder()
                                .bucket(bucket)
                                .build();
                        s3Client.deleteBucket(request);
                }catch (S3Exception e){
                        System.err.println("[DEBUG]: Error deleting bucket " + bucket + ": " + e.getMessage());
                }
        }

        public static void deleteObjectFromBucket(String bucket, String key) { 
                try{
                        DeleteObjectRequest request = DeleteObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build();
                        s3Client.deleteObject(request);
                }catch (S3Exception e){
                        System.err.println("[DEBUG]: Error deleting file " + key + " from bucket " + bucket + ": " + e.getMessage());
                }
        }

        // Local-App-only methods:

        public static boolean isManagerRuning(){
                // First wait for 30 sec, so if there is a manager initializing, it will catch it. A worker instance will not wait.
                try {
                        Thread.sleep(30000);
                } catch (InterruptedException e) {
                        e.printStackTrace();
                }
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
         * If there is no manager running, it will check if the bucket contains the manager jar and the worker jar,
         * because they are nedded for the run, and will upload them if they are not in the bucket.
         * Then it will create a manager instance.
         * @param managerScript
         * @param bucketName
         */
        public static void createManagerIfNotExists(){ 
                if(!isManagerRuning()){
                        createBucketIfNotExists(BUCKET_JAR_NAME); 
                        if(!isFileExistsInBucket(BUCKET_JAR_NAME, managerJarName))
                                uploadFileToS3(BUCKET_JAR_NAME, managerJarName, managerJarPath); 
                        if(!isFileExistsInBucket(BUCKET_JAR_NAME, workerJarName))
                                uploadFileToS3(BUCKET_JAR_NAME, workerJarName, workerJarPath); 
                        
                        createEC2(managerScript, MANAGER_TAG, managerType, 1);
                }
        }

}
