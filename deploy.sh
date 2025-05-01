#!/bin/bash

# Build project
echo "Building project..."
mvn clean package

# Upload to EC2
echo "Uploading to EC2..."
scp -i your-key.pem target/your-app.jar ec2-user@your-ec2-public-ip:/home/ec2-user/

# SSH into EC2 and run the application
echo "Starting application on EC2..."
ssh -i your-key.pem ec2-user@your-ec2-public-ip << 'ENDSSH'
cd /home/ec2-user
nohup java -jar your-app.jar > app.log 2>&1 &
ENDSSH

echo "Deployment completed!" 