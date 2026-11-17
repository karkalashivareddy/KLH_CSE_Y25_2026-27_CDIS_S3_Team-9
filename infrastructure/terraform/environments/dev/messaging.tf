resource "aws_sns_topic" "rabbitmq_stack_events" {
  name              = "${local.name}-rabbitmq-stack-events"
  kms_master_key_id = "alias/aws/sns"
  tags              = local.common_tags
}

resource "aws_cloudformation_stack" "rabbitmq" {
  name               = "${local.name}-rabbitmq"
  on_failure         = "ROLLBACK"
  timeout_in_minutes = 60
  notification_arns  = [aws_sns_topic.rabbitmq_stack_events.arn]
  template_body = jsonencode({
    AWSTemplateFormatVersion = "2010-09-09"
    Description              = "Private CloudResilience RabbitMQ broker with Secrets Manager password reference."
    Resources = {
      Broker = {
        Type                = "AWS::AmazonMQ::Broker"
        DeletionPolicy      = "Retain"
        UpdateReplacePolicy = "Retain"
        Properties = {
          BrokerName              = "${local.name}-rabbitmq"
          EngineType              = "RabbitMQ"
          EngineVersion           = var.rabbitmq_engine_version
          HostInstanceType        = var.mq_instance_type
          DeploymentMode          = "SINGLE_INSTANCE"
          PubliclyAccessible      = false
          SubnetIds               = [module.networking.application_subnet_ids[0]]
          SecurityGroups          = [aws_security_group.broker.id]
          AutoMinorVersionUpgrade = true
          EncryptionOptions = {
            KmsKeyId       = aws_kms_key.application.arn
            UseAwsOwnedKey = false
          }
          Users = [{
            Username      = var.rabbitmq_username
            Password      = "{{resolve:secretsmanager:${aws_secretsmanager_secret.rabbitmq_password.arn}:SecretString}}"
            ConsoleAccess = false
          }]
          Logs = { General = true }
          MaintenanceWindowStartTime = {
            DayOfWeek = "SUNDAY"
            TimeOfDay = "06:00"
            TimeZone  = "UTC"
          }
          Tags = [
            { Key = "Project", Value = "CloudResilience" },
            { Key = "Environment", Value = var.environment },
            { Key = "Owner", Value = var.owner_tag },
            { Key = "ManagedBy", Value = "Terraform" }
          ]
        }
      }
    }
    Outputs = {
      BrokerEndpoint = {
        Value = {
          "Fn::Select" = [0, { "Fn::GetAtt" = ["Broker", "AmqpEndpoints"] }]
        }
      }
    }
  })

  tags = local.common_tags

  lifecycle {
    prevent_destroy = true
  }
}
