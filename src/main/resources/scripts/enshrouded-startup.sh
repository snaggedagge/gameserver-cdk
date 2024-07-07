#!/bin/bash

source /etc/profile.d/environment-variables.sh

TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
IP=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
INSTANCE_ID=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/instance-id)
HOSTED_ZONE_ID="Z2URP9ATDI0RG5"
DOMAIN_NAME="dkarlsso.com."

aws ec2 attach-volume --volume-id "$VOLUME_ID" --instance-id "$INSTANCE_ID" --device /dev/sdf


cd /tmp

cat > change-batch.json << EOF
{
   "Comment": "Add record to the hosted zone",
   "Changes": [
     {
       "Action": "UPSERT",
       "ResourceRecordSet": {
         "Name": "enshrouded.$DOMAIN_NAME",
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


mkdir -p /mnt/game
mkfs -t ext4 /dev/sdf
mount /dev/sdf /mnt/game
chmod -R 777 /mnt/game

# Docs https://github.com/lloesche/valheim-server-docker?tab=readme-ov-file
docker run -d \
    --name enshrouded \
    --cap-add=sys_nice \
    --stop-timeout 120 \
    -p 15636-15637:15636-15637 \
    -p 27015:27015/tcp \
    -p 27031-27036:27031-27036/udp \
    -v /mnt/game/enshrouded:/opt/enshrouded \
    -e SERVER_NAME="SnaGGeNs Server" \
    -e SERVER_PASSWORD="banan" \
    -e UPDATE_CRON="*/30 * * * *" \
    -e SERVER_SLOT_COUNT="3" \
    -e BACKUP_MAX_COUNT="3" \
    mornedhels/enshrouded-server:latest
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