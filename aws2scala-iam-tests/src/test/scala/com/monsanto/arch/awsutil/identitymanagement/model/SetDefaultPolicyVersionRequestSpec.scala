package com.monsanto.arch.awsutil.identitymanagement.model

import com.monsanto.arch.awsutil.converters.IamConverters._
import com.monsanto.arch.awsutil.identitymanagement.AwsMatcherSupport
import com.monsanto.arch.awsutil.testkit.IamScalaCheckImplicits._
import org.scalatest.FreeSpec
import org.scalatest.Matchers._
import org.scalatest.prop.GeneratorDrivenPropertyChecks._

class SetDefaultPolicyVersionRequestSpec extends FreeSpec with AwsMatcherSupport {
  "a SetDefaultPolicyVersionRequest should" - {
    "convert to the correct AWS equivalent" in {
      forAll { request: SetDefaultPolicyVersionRequest ⇒
        request.asAws should have (
          'PolicyArn (request.arn.arnString),
          'VersionId (request.versionId)
        )
      }
    }
  }
}
