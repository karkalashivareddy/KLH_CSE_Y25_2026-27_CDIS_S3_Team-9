data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = "cloudresilience-${var.environment}"

  common_tags = {
    Project     = "CloudResilience"
    Environment = var.environment
    Owner       = var.owner_tag
    ManagedBy   = "Terraform"
  }
}

module "networking" {
  source = "../../modules/networking"

  name               = local.name
  vpc_cidr           = var.vpc_cidr
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)
  tags               = local.common_tags
}

resource "aws_kms_key" "application" {
  description             = "CloudResilience ${var.environment} data encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  tags                    = local.common_tags
}

resource "aws_kms_alias" "application" {
  name          = "alias/${local.name}-data"
  target_key_id = aws_kms_key.application.key_id
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/cloudresilience/${var.environment}/api"
  retention_in_days = 30
  kms_key_id        = aws_kms_key.application.arn
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "web" {
  name              = "/cloudresilience/${var.environment}/web"
  retention_in_days = 14
  kms_key_id        = aws_kms_key.application.arn
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "waf" {
  name              = "aws-waf-logs-cloudresilience-${var.environment}"
  retention_in_days = 30
  kms_key_id        = aws_kms_key.application.arn
  tags              = local.common_tags
}
