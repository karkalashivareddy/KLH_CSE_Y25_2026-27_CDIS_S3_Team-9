output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public edge subnets for the ALB and NAT gateway."
  value       = aws_subnet.public[*].id
}

output "application_subnet_ids" {
  description = "Private application subnets."
  value       = aws_subnet.application[*].id
}

output "database_subnet_ids" {
  description = "Isolated database subnets without a default route."
  value       = aws_subnet.database[*].id
}

output "application_route_table_id" {
  description = "Private application route table for AWS service endpoints."
  value       = aws_route_table.application.id
}
