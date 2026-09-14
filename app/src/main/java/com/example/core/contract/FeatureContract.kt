package com.example.core.contract

/**
 * Feature Contract data structure required by Section 19 of the Master Development Contract.
 */
data class FeatureContract(
  val id: String,
  val name: String,
  val purpose: String,
  val input: String,
  val process: String,
  val output: String,
  val dataSource: String,
  val dependencies: List<String>,
  val successCriteria: String,
  val failureCriteria: String,
  val testProcedure: String,
  val verificationLevel: String,
  val status: FeatureStatus,
  val knownLimitations: String = "",
  val remainingIssues: String = ""
)
