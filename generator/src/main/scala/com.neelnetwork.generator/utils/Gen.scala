package com.neelnetwork.generator.utils

import java.util.concurrent.ThreadLocalRandom

import com.neelnetwork.account.{Address, PrivateKeyAccount}
import com.neelnetwork.generator.utils.Implicits._
import com.neelnetwork.state.{BinaryDataEntry, BooleanDataEntry, ByteStr, DataEntry, IntegerDataEntry, StringDataEntry}
import com.neelnetwork.transaction.smart.script.{Script, ScriptCompiler}
import com.neelnetwork.transaction.transfer.MassTransferTransaction.ParsedTransfer
import com.neelnetwork.transaction.transfer._
import com.neelnetwork.transaction.{Proofs, Transaction}
import com.neelnetwork.utils.LoggerFacade
import org.slf4j.LoggerFactory
import scorex.crypto.signatures.Curve25519._

object Gen {
  private def random = ThreadLocalRandom.current

  val log = LoggerFacade(LoggerFactory.getLogger("Gen"))

  def script(complexity: Boolean = true): Script = {
    val s = if (complexity) s"""
                               |${(for (b <- 1 to 10) yield {
                                 s"let a$b = blake2b256(base58'') != base58'' && keccak256(base58'') != base58'' && sha256(base58'') != base58'' && sigVerify(base58'333', base58'123', base58'567')"
                               }).mkString("\n")}
                               |
                               |${(for (b <- 1 to 10) yield { s"a$b" }).mkString("&&")} || true
       """.stripMargin
    else
      s"""
        |${recString(10)} || true
      """.stripMargin

    val script = ScriptCompiler(s, isAssetScript = false).explicitGet()

    script._1
  }

  def recString(n: Int): String =
    if (n <= 1) "true"
    else
      s"if (${recString(n - 1)}) then true else false"

  def oracleScript(oracle: PrivateKeyAccount, data: Set[DataEntry[_]]): Script = {
    val conditions =
      data.map {
        case IntegerDataEntry(key, value) => s"""(extract(getInteger(oracle, "$key")) == $value)"""
        case BooleanDataEntry(key, _)     => s"""extract(getBoolean(oracle, "$key"))"""
        case BinaryDataEntry(key, value)  => s"""(extract(getBinary(oracle, "$key")) == $value)"""
        case StringDataEntry(key, value)  => s"""(extract(getString(oracle, "$key")) == "$value")"""
      } reduce [String] { case (l, r) => s"$l && $r " }

    val src =
      s"""
         |let oracle = Address(base58'${oracle.address}')
         |
         |match tx {
         |  case _: SetScriptTransaction => true
         |  case _                       => $conditions
         |}
       """.stripMargin

    val script = ScriptCompiler(src, isAssetScript = false).explicitGet()

    script._1
  }

  def multiSigScript(owners: Seq[PrivateKeyAccount], requiredProofsCount: Int): Script = {
    val accountsWithIndexes = owners.zipWithIndex
    val keyLets =
      accountsWithIndexes map {
        case (acc, i) =>
          s"let accountPK$i = base58'${ByteStr(acc.publicKey).base58}'"
      } mkString "\n"

    val signedLets =
      accountsWithIndexes map {
        case (_, i) =>
          s"let accountSigned$i = if(sigVerify(tx.bodyBytes, tx.proofs[$i], accountPK$i)) then 1 else 0"
      } mkString "\n"

    val proofSum = accountsWithIndexes map {
      case (_, ind) =>
        s"accountSigned$ind"
    } mkString ("let proofSum = ", " + ", "")

    val finalStatement = s"proofSum >= $requiredProofsCount"

    val src =
      s"""
       |$keyLets
       |
       |$signedLets
       |
       |$proofSum
       |
       |$finalStatement
      """.stripMargin

    val (script, _) = ScriptCompiler(src, isAssetScript = false)
      .explicitGet()
    log.info(s"${script.text}")
    script
  }

  def txs(minFee: Long, maxFee: Long, senderAccounts: Seq[PrivateKeyAccount], recipientGen: Iterator[Address]): Iterator[Transaction] = {
    val senderGen = Iterator.randomContinually(senderAccounts)
    val feeGen    = Iterator.continually(minFee + random.nextLong(maxFee - minFee))
    transfers(senderGen, recipientGen, feeGen)
  }

  def transfers(senderGen: Iterator[PrivateKeyAccount], recipientGen: Iterator[Address], feeGen: Iterator[Long]): Iterator[Transaction] = {
    senderGen
      .zip(recipientGen)
      .zip(feeGen)
      .map {
        case ((src, dst), fee) =>
          TransferTransactionV1.selfSigned(None, src, dst, fee, System.currentTimeMillis(), None, fee, Array.emptyByteArray)
      }
      .collect { case Right(x) => x }
  }

  def massTransfers(senderGen: Iterator[PrivateKeyAccount], recipientGen: Iterator[Address], amountGen: Iterator[Long]): Iterator[Transaction] = {
    val transferCountGen = Iterator.continually(random.nextInt(MassTransferTransaction.MaxTransferCount + 1))
    senderGen
      .zip(transferCountGen)
      .map {
        case (sender, count) =>
          val transfers = List.tabulate(count)(_ => ParsedTransfer(recipientGen.next(), amountGen.next()))
          val fee       = 100000 + count * 50000
          MassTransferTransaction.selfSigned(Proofs.Version, None, sender, transfers, System.currentTimeMillis, fee, Array.emptyByteArray)
      }
      .collect { case Right(tx) => tx }
  }

  val address: Iterator[Address] = Iterator.continually {
    val pk = Array.fill[Byte](KeyLength)(random.nextInt(Byte.MaxValue).toByte)
    Address.fromPublicKey(pk)
  }

  def address(uniqNumber: Int): Iterator[Address] = Iterator.randomContinually(address.take(uniqNumber).toSeq)

  def address(limitUniqNumber: Option[Int]): Iterator[Address] = limitUniqNumber.map(address(_)).getOrElse(address)

}
