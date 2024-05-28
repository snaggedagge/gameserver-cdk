import { EC2Client, DescribeInstancesCommand, StartInstancesCommand } from "@aws-sdk/client-ec2";
import { AutoScalingClient, SetDesiredCapacityCommand } from "@aws-sdk/client-auto-scaling"; // ES Modules import
const client = new EC2Client({ region: "eu-north-1" });
const asgClient = new AutoScalingClient({ region: "eu-north-1" });
export const handler = async (event) => {
    try {
        console.log('## EVENT: ' + JSON.stringify(event));
        let password = event['queryStringParameters']['password']
        if (password !== 'banan') {
            return {
                statusCode: 200,
                body: JSON.stringify({
                    message: 'Eff off chinese invader'
                })
            };
        }

        const command = new SetDesiredCapacityCommand({
            AutoScalingGroupName: process.env.AUTOSCALING_GROUP_NAME,
            DesiredCapacity: 1,
            HonorCooldown: false,
        });
        const response = await asgClient.send(command);

        // Retrieve the public IP addresses of all 'valheim' instances, regardless of state
        const allValheimInstancesParams = {
            Filters: [
                {
                    Name: 'tag:Name',
                    Values: ['valheim-server/ASG']
                }
            ]
        };
        const allValheimInstancesCommand = new DescribeInstancesCommand(allValheimInstancesParams);
        const allValheimInstances = await client.send(allValheimInstancesCommand);
        const ipAddresses = allValheimInstances.Reservations.flatMap(reservation =>
            reservation.Instances.map(instance => instance.PublicIpAddress)
        );

        return {
            statusCode: 200,
            body: JSON.stringify({
                message: 'Instance is running',
                ipAddresses: ipAddresses
            })
        };

    } catch (error) {
        console.error(error);
        throw new Error(`Error starting instances: ${error.message}`);
    }
}
