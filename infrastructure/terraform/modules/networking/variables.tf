variable "name" {
  description = "Resource name prefix."
  type        = string
}

variable "vpc_cidr" {
  description = "RFC1918 CIDR block for this VPC."
  type        = string
}

variable "availability_zones" {
  description = "Exactly two availability zones to host edge, app, and isolated data subnets."
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 2 && length(distinct(var.availability_zones)) == 2
    error_message = "availability_zones must contain exactly two distinct zones."
  }
}

variable "tags" {
  description = "Tags to apply to networking resources."
  type        = map(string)
  default     = {}
}
