# gameserver-cdk

CDK project that currently only supports booting a valheim server.

Could nicely be extended to support multiple various games though, especially if mixed up with some neat design patterns.

## Infra description

The infrastructure uses a Autoscaling group to boot a single EC2 instance.
ASG's was chosen in order to leverage EC2 spot instances.

But since the EC2 instance will change, I thought it easiest to use EFS for storage since that is quite easily attached to multiple different EC2.
I have discovered however that EBS volumes can be attached programmatically after instance startup and even to multiple instances, so that could be used as well.
EFS cost 0.30$ per-gb-month (0.3$ for 1 GB per month) and EBS costs 0.08 (And even less when not mounted I think).

So for a valheim server of just a few GB's it does not matter much, since it accumulates 1.3$ per month. 
But for games requiring a lot of harddrive, this might need to be reconsidered.


This server is started by entering a URL such as https://valheim-management.dkarlsso.com/start?password=superSecret 
into the browser, which will boot up the EC2 instance before you have had time to start the game and connect.

That is done with a simple Lambda, connected to API Gateway.

Route53 is also used here since I anyway has a domain lying around, but that is not really a requirement.
The EC2 instance registers itself in Route53 on startup, so gameserver is connectable via valheim.dkarlsso.com.


The EC2 instance keeps track of if the server is empty via a simple CRON script running on server. 
So it runs every 30 minutes, and if the server is empty it will update the ASG desired size to 0, 
which will effectively terminate the instance.

That way this setup is quite "serverless", it only costs when someone is actually playing (+ 1.3$ for storage).

In April 2024 I was using a t3.medium server with 4 GB of memory and my friends was playing on like 20 evenings that month.
That accumulated a cost of 2.49$ in EC2 services. 

## Deployment

Currently to get the start-lambda code working, you have to step into the folder and run a `npm install` to init the node modules.
This is a ugly but fast solution, there are constructs in CDK however to make CDK install all node packages when synthing the app, which would be nicer