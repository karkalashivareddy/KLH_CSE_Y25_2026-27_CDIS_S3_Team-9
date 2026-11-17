resource "aws_security_group" "alb" {
  name                   = "${local.name}-alb"
  description            = "Public HTTPS edge."
  vpc_id                 = module.networking.vpc_id
  revoke_rules_on_delete = true
  egress                 = []
  tags                   = merge(local.common_tags, { Name = "${local.name}-alb" })
}

resource "aws_security_group" "application" {
  name                   = "${local.name}-application"
  description            = "Private ECS tasks."
  vpc_id                 = module.networking.vpc_id
  revoke_rules_on_delete = true
  egress                 = []
  tags                   = merge(local.common_tags, { Name = "${local.name}-application" })
}

resource "aws_security_group" "database" {
  name                   = "${local.name}-database"
  description            = "PostgreSQL is reachable only from the ECS application security group."
  vpc_id                 = module.networking.vpc_id
  revoke_rules_on_delete = true
  egress                 = []
  tags                   = merge(local.common_tags, { Name = "${local.name}-database" })
}

resource "aws_security_group" "redis" {
  name                   = "${local.name}-redis"
  description            = "Redis TLS is reachable only from the ECS application security group."
  vpc_id                 = module.networking.vpc_id
  revoke_rules_on_delete = true
  egress                 = []
  tags                   = merge(local.common_tags, { Name = "${local.name}-redis" })
}

resource "aws_security_group" "broker" {
  name                   = "${local.name}-rabbitmq"
  description            = "AMQPS is reachable only from the ECS application security group."
  vpc_id                 = module.networking.vpc_id
  revoke_rules_on_delete = true
  egress                 = []
  tags                   = merge(local.common_tags, { Name = "${local.name}-rabbitmq" })
}

resource "aws_security_group" "endpoints" {
  name                   = "${local.name}-endpoints"
  description            = "Interface VPC endpoints accept HTTPS from ECS tasks only."
  vpc_id                 = module.networking.vpc_id
  revoke_rules_on_delete = true
  egress                 = []
  tags                   = merge(local.common_tags, { Name = "${local.name}-endpoints" })
}

resource "aws_vpc_security_group_ingress_rule" "endpoints_from_application" {
  security_group_id            = aws_security_group.endpoints.id
  description                  = "ECS task execution APIs over HTTPS."
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
  referenced_security_group_id = aws_security_group.application.id
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP redirect to TLS."
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  description       = "Public HTTPS."
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "alb_api" {
  security_group_id            = aws_security_group.alb.id
  description                  = "ALB to API targets."
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.application.id
}

resource "aws_vpc_security_group_ingress_rule" "application_from_alb_api" {
  security_group_id            = aws_security_group.application.id
  description                  = "ALB to API tasks."
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.alb.id
}

resource "aws_vpc_security_group_ingress_rule" "application_service_connect" {
  security_group_id            = aws_security_group.application.id
  description                  = "Frontend to API via ECS Service Connect."
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.application.id
}

resource "aws_vpc_security_group_egress_rule" "application_self" {
  security_group_id            = aws_security_group.application.id
  description                  = "ECS Service Connect traffic between application tasks."
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.application.id
}

resource "aws_vpc_security_group_egress_rule" "application_postgres" {
  security_group_id            = aws_security_group.application.id
  description                  = "Application to PostgreSQL."
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.database.id
}

resource "aws_vpc_security_group_egress_rule" "application_redis" {
  security_group_id            = aws_security_group.application.id
  description                  = "Application to Redis TLS."
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
  referenced_security_group_id = aws_security_group.redis.id
}

resource "aws_vpc_security_group_egress_rule" "application_rabbitmq" {
  security_group_id            = aws_security_group.application.id
  description                  = "Application to RabbitMQ TLS."
  ip_protocol                  = "tcp"
  from_port                    = 5671
  to_port                      = 5671
  referenced_security_group_id = aws_security_group.broker.id
}

resource "aws_vpc_security_group_egress_rule" "application_endpoints" {
  security_group_id            = aws_security_group.application.id
  description                  = "AWS APIs through private interface endpoints only."
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
  referenced_security_group_id = aws_security_group.endpoints.id
}

resource "aws_vpc_security_group_egress_rule" "application_s3" {
  security_group_id = aws_security_group.application.id
  description       = "ECR image layers through the S3 gateway endpoint."
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  prefix_list_id    = data.aws_prefix_list.s3.id
}

resource "aws_vpc_security_group_ingress_rule" "database_from_application" {
  security_group_id            = aws_security_group.database.id
  description                  = "PostgreSQL from ECS application tasks."
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.application.id
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_application" {
  security_group_id            = aws_security_group.redis.id
  description                  = "Redis TLS from ECS application tasks."
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
  referenced_security_group_id = aws_security_group.application.id
}

resource "aws_vpc_security_group_ingress_rule" "broker_from_application" {
  security_group_id            = aws_security_group.broker.id
  description                  = "RabbitMQ TLS from ECS application tasks."
  ip_protocol                  = "tcp"
  from_port                    = 5671
  to_port                      = 5671
  referenced_security_group_id = aws_security_group.application.id
}
