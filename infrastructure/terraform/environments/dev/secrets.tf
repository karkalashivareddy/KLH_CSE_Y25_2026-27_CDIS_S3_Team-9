ephemeral "random_password" "database" {
  length  = 32
  special = false
}

ephemeral "random_password" "redis" {
  length  = 32
  special = false
}

ephemeral "random_password" "rabbitmq" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "database_password" {
  name                    = "${local.name}/postgres/password"
  description             = "Generated application database credential."
  kms_key_id              = aws_kms_key.application.arn
  recovery_window_in_days = 30
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "database_password" {
  secret_id                = aws_secretsmanager_secret.database_password.id
  secret_string_wo         = ephemeral.random_password.database.result
  secret_string_wo_version = 1
}

resource "aws_secretsmanager_secret" "redis_auth" {
  name                    = "${local.name}/redis/auth-token"
  description             = "Generated ElastiCache AUTH token."
  kms_key_id              = aws_kms_key.application.arn
  recovery_window_in_days = 30
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id                = aws_secretsmanager_secret.redis_auth.id
  secret_string_wo         = ephemeral.random_password.redis.result
  secret_string_wo_version = 1
}

resource "aws_secretsmanager_secret" "rabbitmq_password" {
  name                    = "${local.name}/rabbitmq/password"
  description             = "Generated Amazon MQ RabbitMQ credential."
  kms_key_id              = aws_kms_key.application.arn
  recovery_window_in_days = 30
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "rabbitmq_password" {
  secret_id                = aws_secretsmanager_secret.rabbitmq_password.id
  secret_string_wo         = ephemeral.random_password.rabbitmq.result
  secret_string_wo_version = 1
}
