# Sentiment-Analysis and Sarcasm Detection with NLP on AWS

Overview
Welcome to the Sarcasm Detection project! This Maven Java project utilizes various AWS services and natural language processing (NLP) tools to detect sarcasm in reviews of movies and books. This project uses AWS services to preform a high scallibility and flexibility VIA those cloud services.
Project Architecture

How to run the project?
1. Make sure your AWS cardantials are stored in the dufault target for the program to take it from! 
	(Aka - save the cardantials that you get from starting the lab in C:\Users\{YourUsername}\.aws in a file that is called “credantials” - without the .txt ending!). 
	This way your program is secured and there is no need to hard-code the aws crednatials in the program’s code.
2.  Run the program using the terminal using this line: 
	>java -jar LocalAppJar.jar inputFilePath1... inputFilePathN outputFileName1... outputFileNameN n
	When inputFilePath1 is the full path of the input file, the ouput files will be placed authomathically in the same directory as the matching input file. outputFileNamei is only the name, without file type ending (the program creates a html files).
	The n defines how many jobs per a single worker (aka, if a single input file has 400 reviews and n=100 then idealy will be opened 4 workers for this task).
3. Make sure to use “mvn clean package” for the LocalApp, Manegar and Worker projects to compile and build the jar files. 

The project is structured as follows:

LocalApp\src\main\java\LocalApp.java -
The java script for the LocalApp (runs on the costumer computer), which job is to:
1. Creates an s3 bucket, and upload the input files to it. Additionally, uploads to a seperate public bucket the manager and worker JAR files (if not uploaded yet by another costumer).
2. If not created by another local application - creates a joint queue for messeges from the the local applications to the manager. 
3. Creates a unique sqs queue, with purpose to get messeges from the manager that are only relevant to this costumer.
4. Sends a messege to the manager via the joint queue to let him know that there is work to be done for this costumer.
5. Listens to the unique queue. When the manager sent a finished work on a input files, download it from s3 bucket and making an html formatted representing the resaults and saving it in the same folder of the matching input file.
6. After getting back all the jobs from the managet, sends a termintation messege to the manager.
7. Deletes the unique queue and the s3 bucket that was uploaded for the input files. 
*In case the manager is collapsed\got termination messege from another application, the local app detect that the manager is not running and did not finish all the tasks, so it revives the manager.

LocalApp\src\main\java\AWSExecutorLA.java -
Java class that was made for simplifying the work with AWS services, includes all the direct work with AWS services that the LocalApp uses.

LocalApp\src\resources -
Directiry we used for the input.txt files. Be aware that each ouput.html file will be located in the same folder as the matching input file.

Manager\src\main\java\AWSExecutorM.java -
Java class that was made for simplifying the work with AWS services, includes all the direct work with AWS services that the menger uses, and other variables that affects the Manager work.

Manager\src\main\java\Manager.java -
The java script for the manager (runs on t2.small ec2 node), which job is to:
1. Create a threadpool with 10 threads that will manage the direct job with the workers(you can change the threads num in the AWSExecutorM file).
2. Create a joint (used for all the costumers) sqs queue to manage all the jobs for the workers to be done.
3. Recieve messeges from the unique toManager queue, for each messege it the following:
     a. Parse the txt file in the messege to list<string> of the reviews (as JSON object).
     b. Create a unique sqs queue to get the resault from the workes - considering the file number and the local application id.
     c. Calculate the number of workers that are needed for this task, and creating an amount of workers considering how many have been already running.
     d. Put the job needed to be done in the threadpool.
4. If got terminating  messege - closes the threadpool, terminates all worker instances, deleting the toWorkers queue, delete the toManager queue only if it's empty, delete the public S3 bucket with the JAR’s, and terminate itself.
*To avoid lost of data if the manager is collapses, the manager holds a unique backup queue, which stores all the job that were not done yet.
*To handle scalability issues regarding the backup queue, we have made the meneger work only on a several different jobs in perralal.

Manager\src\main\java\MenegerJob.java -
A Java class that implelements Runnable, which is used in the threadpool to manage the workers jobs in a concurrnet way.
The manager job is used for a specific job - aka an input file.
his life cycle:
1. Send each worker's task (aka a review) to the workers queue.
2. Waits and listens to the specific from workers queue.
3. For each messege (aka review) that he got from the queue - decides if it’s sarcastic or not (based in the sentimens), and add the line to a summery.
4. When all the reviews are done for this file, delete the specific queue, uploads the summary to the S3 bucket of the costumer, send the local application a messege to acknolowge that the work has been done.
5. Terminate some of the workers based on how many we created for this job. 

Worker\src\main\java\namedEntityRecognitionHandler.java -
A Java class that is used to make the entity recognition jobs using NLP tools. aka, this class is used for getting a organized list of all the entities from a specific review.

Worker\src\main\java\sentimetnAnalysisHandeler.java -
A Java class that is used to make the setniment analysing jobs using NLP tools. aka, this class is used for getting a sentiment astimation of a specific review (rating 0-4, where 0=very negative and 4=very positive).

Worker\src\main\java\Worker.java -
The java script for the Worker (runs on t2.large ec2 node), which job is to:
1. Gets a job from the joint queue with visability time out to avoid duplication and loss of data.
2. For each job, uses his handler to make a product that has named entity recognition and a sentiment.
3. Sends the product back to the meneger via the specific queue.
4. Deletes the messege from the joint queue.
*In case the meneger collapses, the worker terminates itself.

Worker\src\main\java\AWSExecutorW.java -
Java class that was made for simplifying the work with AWS services, includes all the direct work with AWS services that the worker uses.

README.txt: This file, providing an overview of the project, its architecture, and instructions for usage.
 
Importent additions:
1.	The ami is used for all the EC2 instances in this project is: ami-00e95a9222311e8ed.
2.	Running time of the project with all 5 input files: 2002646 milliseconds, with n = 80.
3.	Scalability – We've managed to make the program highly scalable – it should be working properly even with large number of users using, even though it would run extremely slow due to the the small amount of EC2 nodes that we are aloud to open.
	Nothing "Big" is located in the EC2nodes memory\personal computer. The only problem that we arise from the project that might give us issues is a big amount of messages in the backup queue of the manager – therefore we've limited the amount of jobs that the manager can do simultaneously.
4.	Persistence – What if a EC2 node dies?
	If the manager dies, the local application is reviving him automatically and the "new" manager first looks up for jobs in an backup SQS queue that was made and kept by the old manager.
	In case one of the worker nodes has died, it's not a big deal, any job that a worker takes for himself comes with the "visibility timeout" mechanism, therefore if the worker havn't finished a job yet it will be visible in the toWorker queue in a few minutes.
		The only case that a job is deleted from this queue is when a worker already finished the job of this task and sent back the resault back to the manager to the specific queue.
5.	Using threads – We've used threads in the program only in the manager sections – that's because the manager job is the most flexible job and needs to handle a lot of different requirements – and we didn't want it to work sequently. 
		This make our manger be able to listen to queues, parse jobs to be done, opening new EC2 nodes and managing the workers concurrently.
We don't think using threads in the localApp section is needed due to the sequently way of things that the app needs to do before actually starting the job, and after that it's job is mainly to wait for the system to finish all the work.
We didn’t think it's a good idea that the workers will work concurrently as well, because their actual job is pretty straight forward, and even though using NLP tool require a lot of time, there is not much to be done while waiting for the algorithm to finish it's resault on the named entity recognition and the sentiment handling. 
	All a work needs to do is take 1 review and preform on it, therefore it won't help a lot if it will work concurrently.
6.	Teminating – As explained above in the architecture section – in the end of running the program everything is closed as desired 
		– it was not easy figuring out how to close everything and removing it from the AWS cloud without causing problems to another costumer that is using the program, but we've managed to run it with different costumers and everything is closing up as desired.
7.	Slacking workers – In times when a worker is slacking and not finishing a job, the job will be visible again in the toWorker queue after the visibility timeout expires.

