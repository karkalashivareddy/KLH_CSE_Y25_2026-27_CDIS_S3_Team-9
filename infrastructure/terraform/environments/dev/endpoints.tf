data "aws_partition" "current" {}

resource "aws_vpc_endpoint" "ecr_api" {
  vpc_id              = module.networking.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.ecr.api"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = module.networking.application_subnet_ids
  security_group_ids  = [aws_security_group.endpoints.id]
  tags                = merge(local.common_tags, { Name = "${local.name}-ecr-api" })
}

resource "aws_vpc_endpoint" "ecr_dkr" {
  vpc_id              = module.networking.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.ecr.dkr"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = module.networking.application_subnet_ids
  security_group_ids  = [aws_security_group.endpoints.id]
  tags                = merge(local.common_tags, { Name = "${local.name}-ecr-dkr" })
}

resource "aws_vpc_endpoint" "logs" {
  vpc_id              = module.networking.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.logs"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = module.networking.application_subnet_ids
  security_group_ids  = [aws_security_group.endpoints.id]
  tags                = merge(local.common_tags, { Name = "${local.name}-logs" })
}

resource "aws_vpc_endpoint" "secretsmanager" {
  vpc_id              = module.networking.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.secretsmanager"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = module.networking.application_subnet_ids
  security_group_ids  = [aws_security_group.endpoints.id]
  tags                = merge(local.common_tags, { Name = "${local.name}-secretsmanager" })
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = module.networking.vpc_id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [module.networking.application_route_table_id]
  policy            = data.aws_iam_policy_document.s3_endpoint.json
  tags              = merge(local.common_tags, { Name = "${local.name}-s3" })
}

data "aws_iam_policy_document" "s3_endpoint" {
  statement {
    sid       = "EcrImageLayerDownloads"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["arn:${data.aws_partition.current.partition}:s3:::prod-${var.aws_region}-starport-layer-bucket/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}
