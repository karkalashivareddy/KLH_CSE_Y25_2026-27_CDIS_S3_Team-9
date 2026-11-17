variable "aws_region" {
  description = "AWS region for this environment. Select a region where all chosen services and instance classes are available."
  type        = string
  nullable    = false
}

variable "environment" {
  description = "Short environment name used in resource names and default tags."
  type        = string
  default     = "dev"

  validation {
    condition     = can(regex("^[a-z0-9-]{2,12}$", var.environment))
    error_message = "environment must be 2-12 lowercase letters, numbers, or hyphens."
  }
}

variable "owner_tag" {
  description = "Team or cost-center owner tag."
  type        = string
  default     = "team-9"
}

variable "vpc_cidr" {
  description = "RFC1918 CIDR block for the VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "database_name" {
  description = "PostgreSQL database name."
  type        = string
  default     = "cloudresilience"

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9_]{0,62}$", var.database_name))
    error_message = "database_name must be a valid PostgreSQL identifier."
  }
}

variable "database_username" {
  description = "PostgreSQL application username; password is generated and stored in Secrets Manager."
  type        = string
  default     = "cloudresilience_app"
  sensitive   = true

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9_]{0,62}$", var.database_username))
    error_message = "database_username must be a valid PostgreSQL username."
  }
}

variable "db_instance_class" {
  description = "RDS PostgreSQL instance class. Check availability and price for aws_region before deployment."
  type        = string
  default     = "db.t4g.micro"
}

variable "postgres_engine_version" {
  description = "Explicit PostgreSQL 17 minor version supported by RDS in aws_region."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^17\\.[0-9]+$", var.postgres_engine_version))
    error_message = "postgres_engine_version must be a specific PostgreSQL 17 minor version such as 17.x."
  }
}

variable "database_multi_az" {
  description = "Enable synchronous standby failover. False is development-only and does not provide AZ failover."
  type        = bool
  default     = false
}

variable "db_allocated_storage_gib" {
  description = "Initial encrypted RDS storage in GiB."
  type        = number
  default     = 20

  validation {
    condition     = var.db_allocated_storage_gib >= 20 && var.db_allocated_storage_gib <= 1000
    error_message = "db_allocated_storage_gib must be between 20 and 1000."
  }
}

variable "redis_node_type" {
  description = "ElastiCache node class. Check availability and price for aws_region before deployment."
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_engine_version" {
  description = "ElastiCache Redis OSS engine minor version supported by the selected region."
  type        = string
  default     = "7.1"
}

variable "redis_replicas" {
  description = "Number of Redis replicas. Zero is a low-cost development setting and is not highly available."
  type        = number
  default     = 0

  validation {
    condition     = var.redis_replicas >= 0 && var.redis_replicas <= 5
    error_message = "redis_replicas must be between 0 and 5."
  }
}

variable "mq_instance_type" {
  description = "Amazon MQ RabbitMQ instance type. Verify current engine/class compatibility and regional pricing."
  type        = string
  default     = "mq.t3.micro"
}

variable "rabbitmq_engine_version" {
  description = "Amazon MQ RabbitMQ engine minor supported by the selected instance type and region."
  type        = string
  default     = "3.13"
}

variable "rabbitmq_username" {
  description = "Amazon MQ application username; password is generated and stored in Secrets Manager."
  type        = string
  default     = "cloudresilience_app"
  sensitive   = true
}

variable "api_image_tag" {
  description = "Immutable 40-character commit SHA for the API image already pushed to ECR."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[0-9a-f]{40}$", var.api_image_tag))
    error_message = "api_image_tag must be a lowercase 40-character Git commit SHA."
  }
}

variable "web_image_tag" {
  description = "Immutable 40-character commit SHA for the web image already pushed to ECR."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[0-9a-f]{40}$", var.web_image_tag))
    error_message = "web_image_tag must be a lowercase 40-character Git commit SHA."
  }
}

variable "acm_certificate_arn" {
  description = "ACM certificate ARN in aws_region for the public HTTPS listener."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^arn:[^:]+:acm:[^:]+:[0-9]{12}:certificate/.+$", var.acm_certificate_arn))
    error_message = "acm_certificate_arn must be a certificate ARN in the selected region and account."
  }
}

variable "api_desired_count" {
  description = "Desired API task count. A count of one is for development only."
  type        = number
  default     = 1

  validation {
    condition     = var.api_desired_count >= 1 && var.api_desired_count <= 10
    error_message = "api_desired_count must be between 1 and 10."
  }
}

variable "web_desired_count" {
  description = "Desired web task count. A count of one is for development only."
  type        = number
  default     = 1

  validation {
    condition     = var.web_desired_count >= 1 && var.web_desired_count <= 10
    error_message = "web_desired_count must be between 1 and 10."
  }
}
