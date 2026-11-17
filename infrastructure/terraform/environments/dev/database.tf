resource "aws_db_subnet_group" "application" {
  name       = "${local.name}-database"
  subnet_ids = module.networking.database_subnet_ids
  tags       = merge(local.common_tags, { Name = "${local.name}-database" })
}

resource "aws_db_parameter_group" "application" {
  name_prefix = "${local.name}-postgres17-"
  family      = "postgres17"
  description = "CloudResilience PostgreSQL 17 parameters."

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }

  tags = local.common_tags
}

resource "aws_db_instance" "application" {
  #checkov:skip=CKV_AWS_157:database_multi_az=false is a dev-only cost default; staging and production must set database_multi_az=true.
  identifier                          = "${local.name}-postgres"
  engine                              = "postgres"
  engine_version                      = var.postgres_engine_version
  instance_class                      = var.db_instance_class
  allocated_storage                   = var.db_allocated_storage_gib
  max_allocated_storage               = max(var.db_allocated_storage_gib * 2, 100)
  storage_type                        = "gp3"
  storage_encrypted                   = true
  kms_key_id                          = aws_kms_key.application.arn
  db_name                             = var.database_name
  username                            = var.database_username
  password_wo                         = ephemeral.random_password.database.result
  password_wo_version                 = 1
  port                                = 5432
  db_subnet_group_name                = aws_db_subnet_group.application.name
  vpc_security_group_ids              = [aws_security_group.database.id]
  parameter_group_name                = aws_db_parameter_group.application.name
  publicly_accessible                 = false
  multi_az                            = var.database_multi_az
  backup_retention_period             = 7
  backup_window                       = "02:00-03:00"
  maintenance_window                  = "sun:03:00-sun:04:00"
  auto_minor_version_upgrade          = true
  deletion_protection                 = true
  copy_tags_to_snapshot               = true
  enabled_cloudwatch_logs_exports     = ["postgresql", "upgrade"]
  monitoring_interval                 = 60
  monitoring_role_arn                 = aws_iam_role.rds_monitoring.arn
  iam_database_authentication_enabled = true
  skip_final_snapshot                 = false
  final_snapshot_identifier           = "${local.name}-postgres-final"
  apply_immediately                   = false
  tags                                = merge(local.common_tags, { Name = "${local.name}-postgres" })

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_iam_role" "rds_monitoring" {
  name = "${local.name}-rds-monitoring"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}
