package org.inpertio.server.git.service.impl

import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Git operations are relatively slow, so, if config repo has multiple branches and the config service tries
 * to update all of them every time, it's rather slow. That's why we limit it only to the branches requested
 * by clients.
 *
 * This class manages that target branches info.
 */
@Component
class TargetBranchesManager {

    private val _targetBranches: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val targetBranches: Set<String>
        get() = _targetBranches

    fun onTargetBranch(branch: String) {
        _targetBranches += branch
    }

    fun onRemovedBranch(branch: String) {
        _targetBranches -= branch
    }
}