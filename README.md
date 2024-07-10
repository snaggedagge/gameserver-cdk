# gameserver-cdk

CDK project that currently supports booting a Valheim or Enshrouded server.

## Infra description

The infrastructure uses a Autoscaling group to boot a single EC2 instance.
ASG's was chosen in order to leverage EC2 spot instances.

It is possible to choose between using EBS for storage, or EFS.

The game servers can be started by going to http://game-management.dkarlsso.com and selecting the one you want.
The GUI is presented with a simple S3 bucket, and data is sourced from a JSON file which aws constructed and deployed to S3 during deployment.

A small lambda connected to an API Gateway boots up the game server by changing desired size of ASG to 1.

The EC2 instance keeps track of if the server is empty via a simple CRON script running on server. 
So it runs every 30 minutes, and if the server is empty it will update the ASG desired size to 0, 
which will effectively terminate the instance.

That way this setup is quite "serverless", it only costs when someone is actually playing (+ 1.3$ for storage).

In April 2024 I was using a t3.medium server with 4 GB of memory and my friends was playing on like 20 evenings that month.
That accumulated a cost of 2.49$ in EC2 services. 

## Deployment

Currently to get the start-lambda code working, you have to step into the folder and run a `npm install` to init the node modules.
This is a ugly but fast solution, there are constructs in CDK however to make CDK install all node packages when synthing the app, which would be nicer