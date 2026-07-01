package com.mylive.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkflowPolicyTest {

    @Test
    fun releaseBuildChecksOutTheRequestedTag() {
        val workflow = File("../.github/workflows/release-apk.yml").readText()
        val checkout = workflow.substringAfter("uses: actions/checkout@v4")
            .substringBefore("- name: Set up JDK")

        assertTrue(checkout.contains("ref: \${{ env.RELEASE_TAG }}"))
    }

    @Test
    fun pullRequestsAndMainPushesRunCoreAndroidChecks() {
        val workflowFile = File("../.github/workflows/ci.yml")
        assertTrue("CI workflow must exist before V2 is merged", workflowFile.isFile)

        val workflow = workflowFile.readText()
        assertTrue(workflow.contains("pull_request:"))
        assertTrue(workflow.contains("push:"))
        assertTrue(workflow.contains(":app:testDebugUnitTest"))
        assertTrue(workflow.contains(":app:assembleDebug"))
        assertTrue(workflow.contains(":app:lintDebug"))
    }
}
