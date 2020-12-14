package org.inpertio.server.git.service.impl

import org.eclipse.jgit.api.Git
import org.inpertio.server.git.config.LocalGitParameters
import org.inpertio.server.git.service.GitService
import org.inpertio.server.git.config.RemoteGitParameters
import org.inpertio.server.git.model.BranchUpdateResult
import org.inpertio.server.git.model.GitBranch
import org.inpertio.server.git.model.RepoUpdateResult
import org.inpertio.server.git.model.RepoUpdateResult.BranchesUpdateResults
import org.inpertio.server.git.model.RepoUpdateResult.RepoUpdateFailure
import org.inpertio.server.util.FileUtil
import org.inpertio.server.util.ProcessingResult
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

@Component
class GitServiceImpl(
    private val targetBranchesManager: TargetBranchesManager,
    private val localGitParameters: LocalGitParameters,
    private val remoteGitParameters: RemoteGitParameters,
    private val logger: Logger
) : GitService {

    // We use the following design here:
    //   *) <local-data-root>/repo - local dir which is used for retrieving changes from remote git repo
    //   *) <local-data-root>/repo/<branch-name> - a directory per-branch, used for retrieving target branch
    //                                             changes from remote repo
    //   *) <local-data-root>/content - root dir which holds the data for serving actual config requests
    //   *) <local-data-root>/content/<branch-name> - a directory per-branch, its sub-directories contain
    //                                                target branch content snapshots
    //   *) <local-data-root>/content/<branch-name>/<hash> - holds remote Git repo content for the target commit

    private val branchRegistry = ConcurrentHashMap<String/* branch name */, BranchRecord>()

    override fun <T> withBranch(branch: String, action: GitService.Action<T>): T? {
        targetBranchesManager.onTargetBranch(branch)
        return withBranch(branch, action) {
            update()
            withBranch(branch, action) {
                null
            }
        }
    }

    private fun <T> withBranch(branch: String, branchAction: GitService.Action<T>, onAbsentBranchAction: () -> T?): T? {
        return branchRegistry[branch]?.let { record ->
            if (record.rootDir.isDirectory) {
                record.lock.readLock().withLock {
                    branchAction.doInBranch(record.hash, record.rootDir)
                }
            } else {
                onAbsentBranchAction()
            }
        } ?: onAbsentBranchAction()
    }

    private fun update(): RepoUpdateResult {
        val listBranchesResult = listRemoteBranches(remoteGitParameters)
        if (!listBranchesResult.success) {
            return listBranchesResult.failureValue
        }

        val remoteBranchNames = listBranchesResult.successValue.map { it.name }.toSortedSet()
        logger.info("Found {} remote branches: {}", remoteBranchNames.size, remoteBranchNames)

        val (activeBranches, removedBranches) = targetBranchesManager.targetBranches.partition {
            remoteBranchNames.contains(it)
        }
        if (removedBranches.isNotEmpty()) {
            logger.info("{} branches are not found in remote repo, removing them from active branches list: {}",
                        removedBranches.size, removedBranches)
            removedBranches.forEach(targetBranchesManager::onRemovedBranch)
        }

        val repoRoot = File(localGitParameters.rootDir, "repo")
        cleanUpRemovedBranches(remoteBranchNames, repoRoot)

        return BranchesUpdateResults(activeBranches.map { branchName ->
            val branchRepoRootDir = File(repoRoot, branchName)
            prepareLocalRepo(branchRepoRootDir, branchName).apply {
                if (this is BranchUpdateResult.Success) {
                    onUpdatedBranch(this.branch, branchRepoRootDir)
                }
            }
        })
    }

    private fun listRemoteBranches(
        parameters: RemoteGitParameters
    ): ProcessingResult<Collection<GitBranch>, RepoUpdateFailure> {
        return try {
            ProcessingResult.success(Git.lsRemoteRepository()
                                         .setTags(false)
                                         .setHeads(true)
                                         .setRemote(parameters.uri)
                                         .callAsMap()
                                         .map { (branchName, gitRef) ->
                                             GitBranch(getBranchName(branchName), gitRef.objectId.name)
                                         }
            )
        } catch (e: Throwable) {
            logger.warn("Got an exception on attempt to list remote branches", e)
            ProcessingResult.failure(RepoUpdateFailure("failed to list available branches", e))
        }
    }

    private fun getBranchName(qualifiedBranchName: String): String {
        val i = qualifiedBranchName.lastIndexOf("/")
        return if (i > 0) {
            qualifiedBranchName.substring(i + 1)
        } else {
            qualifiedBranchName
        }
    }

    private fun cleanUpRemovedBranches(availableRemoteBranches: Set<String>, branchesRootDir: File) {
        branchesRootDir.listFiles()?.let {
            for (branchDir in it) {
                if (!availableRemoteBranches.contains(branchDir.name)) {
                    try {
                        branchDir.deleteRecursively()
                        logger.info("Removed local git repo for obsolete branch '{}' ({})",
                                    branchDir.name, branchDir.absolutePath)
                    } catch (e: Throwable) {
                        logger.warn("Got an exception on attempt to remove a local git repo for "
                                    + "obsolete branch '{}' ({})", branchDir.name, branchDir.absolutePath, e)
                    }
                }
            }
        }
    }

    private fun prepareLocalRepo(branchRepoRootDir: File, branchName: String): BranchUpdateResult {
        try {
            updateExistingLocalRepo(branchRepoRootDir)?.let { headHash ->
                return BranchUpdateResult.Success(GitBranch(branchName, headHash))
            }
        } catch (e: Throwable) {
            logger.warn("Got an exception on attempt to update local git repo at {}", branchRepoRootDir.absolutePath)
        }

        return try {
            clone(branchRepoRootDir, branchName)
        } catch (e: Throwable) {
            logger.warn("Failed cloning remote git branch {} into {}", branchName, branchRepoRootDir.absolutePath, e)
            BranchUpdateResult.Failure(branchName, "failed cloning branch '$branchName'", e)
        }
    }

    private fun updateExistingLocalRepo(localRepoRootDir: File): String? {
        if (!localRepoRootDir.isDirectory) {
            logger.info("No local git repo is found at {}", localRepoRootDir.absolutePath)
            return null
        }

        val result = Git.open(localRepoRootDir).pull().apply {
            remote = "origin"
        }.call()
        return if (result.isSuccessful) {
            val head = result.mergeResult.newHead.name
            logger.info("Updated local git repo at {} to HEAD {}", localRepoRootDir.absolutePath, head)
            head
        } else {
            logger.warn("Failed to update local git repo at {}", localRepoRootDir.absolutePath)
            null
        }
    }

    private fun clone(localRepoRootDir: File, branchName: String): BranchUpdateResult {
        logger.info("Preparing new local git repo for branch '{}' at {}", branchName, localRepoRootDir.absolutePath)
        if (localRepoRootDir.exists()) {
            if (!localRepoRootDir.deleteRecursively()) {
                logger.warn("Failed to remove directory {}", localRepoRootDir.absolutePath)
            }
        }
        FileUtil.ensureDirectoryExists(localRepoRootDir)
        val git = Git.cloneRepository()
            .setDirectory(localRepoRootDir)
            .setBranch(branchName)
            .setURI(remoteGitParameters.uri)
            .call()
        logger.info("Successfully prepared local git repo for branch '{}' at {}",
                    branchName, localRepoRootDir.absolutePath)
        val headHash = git.log().setMaxCount(1).call().first().id.name
        return BranchUpdateResult.Success(GitBranch(branchName, headHash))
    }

    private fun onUpdatedBranch(branch: GitBranch, branchRepoRootDir: File) {
        branchRegistry.compute(branch.name) { _, record ->
            if (record?.hash == branch.hash) {
                return@compute record
            }

            val storedRootDir = storeBranchContent(branch, branchRepoRootDir) ?: return@compute record
            record?.lock?.writeLock()?.withLock {
                try {
                    record.rootDir.delete()
                    logger.info("Removed obsolete content for branch '{}' (hash {}) in {}",
                                branch.name, branch.hash, record.rootDir.absolutePath)
                } catch (e: Throwable) {
                    logger.warn("Got an exception on attempt to remove obsolete content for branch '{}' "
                                + "(hash {}) in {}", branch.name, branch.hash, record.rootDir.absolutePath)
                }
            }
            BranchRecord(branch.hash, storedRootDir)
        }
    }

    private fun storeBranchContent(branch: GitBranch, branchRootDir: File): File? {
        val newRootDir = File(localGitParameters.rootDir, "content/${branch.name}/${branch.hash}")
        if (newRootDir.exists()) {
            if (newRootDir.deleteRecursively()) {
                logger.info("Removed existing root dir for branch '{}': {}", branch.name, newRootDir.absolutePath)
            } else {
                logger.warn("Failed to remove existing root dir for branch '{}': {}",
                            branch.name, newRootDir.absolutePath)
                return null
            }
        }

        FileUtil.ensureDirectoryExists(newRootDir)
        branchRootDir.copyRecursively(newRootDir, onError = FILE_COPY_ERROR_HANDLER)
        logger.info("Stored content for branch '{}' and hash {} in {}",
                    branch.name, branch.hash, newRootDir.absolutePath)
        return newRootDir
    }

    companion object {
        private val FILE_COPY_ERROR_HANDLER: (File, IOException) -> OnErrorAction = { _, _ ->
            OnErrorAction.SKIP
        }
    }

    private data class BranchRecord(val hash: String, val rootDir: File) {
        val lock = ReentrantReadWriteLock()
    }
}