package com.neelnetwork.api.http.assets

import cats.implicits._
import com.neelnetwork.account.PublicKeyAccount
import com.neelnetwork.api.http.BroadcastRequest
import com.neelnetwork.transaction.transfer.MassTransferTransaction.Transfer
import com.neelnetwork.transaction.transfer._
import com.neelnetwork.transaction.{AssetIdStringLength, Proofs, ValidationError}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import play.api.libs.functional.syntax._
import play.api.libs.json._

object SignedMassTransferRequest {
  implicit val MassTransferRequestReads: Reads[SignedMassTransferRequest] = (
    (JsPath \ "version").read[Byte] and
      (JsPath \ "senderPublicKey").read[String] and
      (JsPath \ "assetId").readNullable[String] and
      (JsPath \ "transfers").read[List[Transfer]] and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "attachment").readNullable[String] and
      (JsPath \ "proofs").read[List[ProofStr]]
  )(SignedMassTransferRequest.apply _)
}

@ApiModel(value = "Signed Asset transfer transaction")
case class SignedMassTransferRequest(@ApiModelProperty(required = true)
                                     version: Byte,
                                     @ApiModelProperty(value = "Base58 encoded sender public key", required = true)
                                     senderPublicKey: String,
                                     @ApiModelProperty(value = "Base58 encoded Asset ID")
                                     assetId: Option[String],
                                     @ApiModelProperty(value = "List of (recipient, amount) pairs", required = true)
                                     transfers: List[Transfer],
                                     @ApiModelProperty(required = true)
                                     fee: Long,
                                     @ApiModelProperty(required = true)
                                     timestamp: Long,
                                     @ApiModelProperty(value = "Base58 encoded attachment")
                                     attachment: Option[String],
                                     @ApiModelProperty(required = true)
                                     proofs: List[String])
    extends BroadcastRequest {
  def toTx: Either[ValidationError, MassTransferTransaction] =
    for {
      _sender     <- PublicKeyAccount.fromBase58String(senderPublicKey)
      _assetId    <- parseBase58ToOption(assetId.filter(_.length > 0), "invalid.assetId", AssetIdStringLength)
      _proofBytes <- proofs.traverse(s => parseBase58(s, "invalid proof", Proofs.MaxProofStringSize))
      _proofs     <- Proofs.create(_proofBytes)
      _attachment <- parseBase58(attachment.filter(_.length > 0), "invalid.attachment", TransferTransaction.MaxAttachmentStringSize)
      _transfers  <- MassTransferTransaction.parseTransfersList(transfers)
      t           <- MassTransferTransaction.create(version, _assetId, _sender, _transfers, timestamp, fee, _attachment.arr, _proofs)
    } yield t
}
