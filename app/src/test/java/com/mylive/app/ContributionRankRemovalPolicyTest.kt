package com.mylive.app

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ContributionRankRemovalPolicyTest {

    @Test
    fun productionSourcesDoNotContainContributionRankFeature() {
        val sourceFiles = File("src/main")
            .walkTopDown()
            .filter(File::isFile)
            .filter { it.extension in setOf("kt", "xml") }
            .toList()

        val remainingReferences = sourceFiles.filter { file ->
            val source = file.readText()
            source.contains("ContributionRank") ||
                source.contains("contributionRank") ||
                source.contains("贡献榜")
        }

        assertFalse(
            "Contribution rank references remain: ${remainingReferences.joinToString { it.path }}",
            remainingReferences.isNotEmpty()
        )
    }
}
