#!/bin/bash

TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
IP=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
HOSTED_ZONE_ID="Z2URP9ATDI0RG5"
DOMAIN_NAME="dkarlsso.com."
cd /tmp

cat > change-batch.json << EOF
{
   "Comment": "Add record to the hosted zone",
   "Changes": [
     {
       "Action": "UPSERT",
       "ResourceRecordSet": {
         "Name": "valheim.$DOMAIN_NAME",
         "Type": "A",
         "TTL": 60,
         "ResourceRecords": [
           {
             "Value": "$IP"
           }
         ]
       }
     }
   ]
}
EOF

aws route53 change-resource-record-sets --hosted-zone-id $HOSTED_ZONE_ID --change-batch file://change-batch.json
yum install docker -y
systemctl start docker
# Docs https://github.com/lloesche/valheim-server-docker?tab=readme-ov-file
docker run -d \
    --name valheim-server \
    --cap-add=sys_nice \
    --stop-timeout 120 \
    -p 2456-2458:2456-2458/udp \
    -p 9001:9001/tcp \
    -p 80:80/tcp \
    -v /efs/valheim-server/config:/config \
    -v /efs/valheim-server/data:/opt/valheim \
    -v /efs/logs:/home/valheim/Steam/logs \
    -e SERVER_NAME="SnaGGeNs Server" \
    -e WORLD_NAME="Bak i Tranvik" \
    -e SERVER_PASS="banan" \
    -e STATUS_HTTP="true" \
    -e SUPERVISOR_HTTP="true" \
    -e SUPERVISOR_HTTP_PASS="banan" \
    ghcr.io/lloesche/valheim-server
# STATUS_HTTP enables a /status.json resource
# Supervisor enabled on port 9001
curl --silent --location https://rpm.nodesource.com/setup_20.x | bash -
yum -y install nodejs
npm install -g gamedig

yum install cronie -y
systemctl enable crond.service
systemctl start crond.service

crontab -l > mycron
echo "*/30 * * * * /tmp/check-players.sh" >> mycron
crontab mycron
rm mycron