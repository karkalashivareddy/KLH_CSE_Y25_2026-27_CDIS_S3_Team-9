resource "aws_elasticache_subnet_group" "application" {
  name       = "${local.name}-redis"
  subnet_ids = module.networking.database_subnet_ids
  tags       = merge(local.common_tags, { Name = "${local.name}-redis" })
}

resource "aws_elasticache_replication_group" "application" {
  #checkov:skip=CKV_AWS_31:Transit encryption and write-only auth_token_wo are enabled; Checkov does not recognize the provider's write-only token argument.
  replication_group_id       = "${local.name}-redis"
  description                = "CloudResilience distributed web-session store."
  engine                     = "redis"
  engine_version             = var.redis_engine_version
  node_type                  = var.redis_node_type
  port                       = 6379
  parameter_group_name       = "default.redis7"
  num_cache_clusters         = var.redis_replicas + 1
  automatic_failover_enabled = var.redis_replicas > 0
  multi_az_enabled           = var.redis_replicas > 0
  subnet_group_name          = aws_elasticache_subnet_group.application.name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  kms_key_id                 = aws_kms_key.application.arn
  transit_encryption_enabled = true
  auth_token_wo              = ephemeral.random_password.redis.result
  auth_token_wo_version      = 1
  auth_token_update_strategy = "SET"
  snapshot_retention_limit   = 1
  snapshot_window            = "04:00-05:00"
  maintenance_window         = "sun:05:00-sun:06:00"
  apply_immediately          = false
  auto_minor_version_upgrade = true
  tags                       = merge(local.common_tags, { Name = "${local.name}-redis" })

  lifecycle {
    prevent_destroy = true
  }
}
