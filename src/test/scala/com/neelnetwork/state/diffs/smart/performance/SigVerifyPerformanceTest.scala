package com.neelnetwork.state.diffs.smart.performance

import com.neelnetwork.account.{PrivateKeyAccount, PublicKeyAccount}
import com.neelnetwork.lagonaki.mocks.TestBlock
import com.neelnetwork.lang.ScriptVersion.Versions.V1
import com.neelnetwork.lang.v1.compiler.CompilerV1
import com.neelnetwork.lang.v1.compiler.Terms._
import com.neelnetwork.lang.v1.parser.Parser
import com.neelnetwork.metrics.Instrumented
import com.neelnetwork.state._
import com.neelnetwork.state.diffs._
import com.neelnetwork.state.diffs.smart._
import com.neelnetwork.transaction.GenesisTransaction
import com.neelnetwork.transaction.smart.script.v1.ScriptV1
import com.neelnetwork.transaction.transfer._
import com.neelnetwork.utils._
import com.neelnetwork.{NoShrink, TransactionGen, WithDB}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}

class SigVerifyPerformanceTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink with WithDB {

  private val AmtOfTxs = 10000

  private def simpleSendGen(from: PrivateKeyAccount, to: PublicKeyAccount, ts: Long): Gen[TransferTransactionV1] =
    for {
      amt <- smallFeeGen
      fee <- smallFeeGen
    } yield TransferTransactionV1.selfSigned(None, from, to.toAddress, amt, ts, None, fee, Array.emptyByteArray).explicitGet()

  private def scriptedSendGen(from: PrivateKeyAccount, to: PublicKeyAccount, ts: Long): Gen[TransferTransactionV2] =
    for {
      version <- Gen.oneOf(TransferTransactionV2.supportedVersions.toSeq)
      amt     <- smallFeeGen
      fee     <- smallFeeGen
    } yield TransferTransactionV2.selfSigned(version, None, from, to.toAddress, amt, ts, None, fee, Array.emptyByteArray).explicitGet()

  private def differentTransfers(typed: EXPR) =
    for {
      master    <- accountGen
      recipient <- accountGen
      ts        <- positiveIntGen
      amt       <- smallFeeGen
      fee       <- smallFeeGen
      genesis = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
      setScript <- selfSignedSetScriptTransactionGenP(master, ScriptV1(typed).explicitGet())
      transfer       = simpleSendGen(master, recipient, ts)
      scriptTransfer = scriptedSendGen(master, recipient, ts)
      transfers       <- Gen.listOfN(AmtOfTxs, transfer)
      scriptTransfers <- Gen.listOfN(AmtOfTxs, scriptTransfer)
    } yield (genesis, setScript, transfers, scriptTransfers)

  ignore("parallel native signature verification vs sequential scripted signature verification") {
    val textScript    = "sigVerify(tx.bodyBytes,tx.proofs[0],tx.senderPk)"
    val untypedScript = Parser(textScript).get.value
    val typedScript   = CompilerV1(compilerContext(V1, isAssetScript = false), untypedScript).explicitGet()._1

    forAll(differentTransfers(typedScript)) {
      case (gen, setScript, transfers, scriptTransfers) =>
        def simpleCheck(): Unit = assertDiffAndState(Seq(TestBlock.create(Seq(gen))), TestBlock.create(transfers), smartEnabledFS) { case _ => }
        def scriptedCheck(): Unit =
          assertDiffAndState(Seq(TestBlock.create(Seq(gen, setScript))), TestBlock.create(scriptTransfers), smartEnabledFS) {
            case _ =>
          }

        val simeplCheckTime   = Instrumented.withTime(simpleCheck())._2
        val scriptedCheckTime = Instrumented.withTime(scriptedCheck())._2
        println(s"[parallel] simple check time: $simeplCheckTime ms,\t [seqential] scripted check time: $scriptedCheckTime ms")
    }

  }
}
