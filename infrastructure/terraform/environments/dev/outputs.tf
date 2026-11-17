output "load_balancer_dns_name" {
  description = "Public ALB DNS name. Attach the matching Route 53 record after provisioning."
  value       = aws_lb.application.dns_name
}

output "api_ecr_repository_url" {
  description = "Immutable-tag API image repository."
  value       = aws_ecr_repository.api.repository_url
}

output "web_ecr_repository_url" {
  description = "Immutable-tag web image repository."
  value       = aws_ecr_repository.web.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster hosting the API and web services."
  value       = aws_ecs_cluster.application.name
}

output "api_service_name" {
  description = "API ECS service name."
  value       = aws_ecs_service.api.name
}

output "web_service_name" {
  description = "Web ECS service name."
  value       = aws_ecs_service.web.name
}

output "database_endpoint" {
  description = "Private RDS endpoint for in-VPC application troubleshooting."
  value       = aws_db_instance.application.address
  sensitive   = true
}

output "redis_endpoint" {
  description = "Private Redis TLS endpoint for in-VPC application troubleshooting."
  value       = aws_elasticache_replication_group.application.primary_endpoint_address
  sensitive   = true
}

output "rabbitmq_endpoint" {
  description = "Private AMQPS endpoint."
  value       = local.rabbitmq_endpoint
  sensitive   = true
}
