package org.inpertio.server.git.model

sealed class RepoUpdateResult {

    data class RepoUpdateFailure(val error: String, val e: Throwable? = null) : RepoUpdateResult()

    data class BranchesUpdateResults(val result: Collection<BranchUpdateResult>) : RepoUpdateResult()
}

sealed class BranchUpdateResult {

    data class Success(val branch: GitBranch) : BranchUpdateResult()

    data class Failure(val branchName: String, val error: String, val e: Throwable? = null) : BranchUpdateResult()
}