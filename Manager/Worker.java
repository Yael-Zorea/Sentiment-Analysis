import java.util.List;

import org.json.JSONObject;

import software.amazon.awssdk.services.sqs.model.Message;

public class Worker {

    static sentimentAnalysisHandler sentimentAnalysisHandler = new sentimentAnalysisHandler();
    static namedEntityRecognitionHandler namedEntityRecognitionHandler = new namedEntityRecognitionHandler();

    static final String del = AWSExecutorW.delimiter;
    static boolean isManagerAlive = true;

    public static void main(String[] args) {

        /*System.out.println();
        long startTime = System.currentTimeMillis();
        String check = namedEntityRecognitionHandler.getEntitiesInStrFormat("Obama was the president of the US. He was born in Hawaii. Hana was very pretty today, she went to disneyland.");
        System.out.println(check);
        long endTime = System.currentTimeMillis();
        long runTime = endTime - startTime;
        System.out.println("Run time: " + runTime + " milliseconds");
        System.out.println();*/
        
        while(isManagerAlive){
            // Recieve tasks from the Manager
            List<Message> list = AWSExecutorW.receiveMessagesFromQueueWithVT(AWSExecutorW.TO_WORKERS_QUEUE_NAME);
            // The max num of messages that can be received is 1
            for(Message message : list){ 
                String body = message.body();
                // Task for worker format: "reviewJsonObj, fileNum, localApplicationID" separated by delimiter
                String[] taskArgs = body.split(del);
                JSONObject review = new JSONObject(taskArgs[0]);
                int fileNum = Integer.parseInt(taskArgs[1]);
                String localAppID = taskArgs[2];

                // Parse the review as a JSON object
                //String id = review.getString("id"); 
                String reviewLink = review.getString("link");
                //String reviewTitle = review.getString("title");
                String reviewText = review.getString("text").replace("\"", "''");
                System.out.println("Review text: " + reviewText);
                int rating = review.getInt("rating");
                //String author = review.getString("author");
                //String date = review.getString("date");

                String specificQueueN = AWSExecutorW.FROM_WORKER_QUEUE_NAME + localAppID + fileNum; 
                System.out.println("Sending to queue: " + specificQueueN);

                // Product from worker format: "reviewLink, reviewRating, sentiment, entityRecStr(= NONE if there is no entities)" separated by delimiter
                int sentiment = sentimentAnalysisHandler.findSentiment(reviewText);
                String entityRecStr = namedEntityRecognitionHandler.getEntitiesInStrFormat(reviewText);
                if(entityRecStr == "") entityRecStr = "NONE";
                String product = reviewLink + del + rating + del + sentiment + del + entityRecStr;
                System.out.println("Product: " + product + ", File num: " + fileNum + ", LocalAppID: " + localAppID);
                AWSExecutorW.sendMessageToQueue(specificQueueN, product);

                // Delete the message from the worker's task queue, because the task is done
                AWSExecutorW.deleteMessageFromQueue(AWSExecutorW.TO_WORKERS_QUEUE_NAME, message);    
            }

            // Check if the manager is still alive
            isManagerAlive = AWSExecutorW.isManagerRunning(); 
        } 
        
        /* End of while loop = The manager is not running and didn't shut the worker down =
            = The manager collapsed --> need to terminate myself.
            Note: This is THE ONLY scenario where a worker terminates itself! */
        AWSExecutorW.terminateMyself(); 
        
    }

}
