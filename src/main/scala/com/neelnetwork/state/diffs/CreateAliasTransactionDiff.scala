package com.neelnetwork.state.diffs

import com.neelnetwork.features.BlockchainFeatures
import com.neelnetwork.state.{Blockchain, Diff, LeaseBalance, Portfolio}
import com.neelnetwork.transaction.ValidationError.GenericError
import com.neelnetwork.transaction.{CreateAliasTransaction, ValidationError}
import com.neelnetwork.features.FeatureProvider._

import scala.util.Right

object CreateAliasTransactionDiff {
  def apply(blockchain: Blockchain, height: Int)(tx: CreateAliasTransaction): Either[ValidationError, Diff] =
    if (blockchain.isFeatureActivated(BlockchainFeatures.DataTransaction, height) && !blockchain.canCreateAlias(tx.alias))
      Left(GenericError("Alias already claimed"))
    else
      Right(
        Diff(height = height,
             tx = tx,
             portfolios = Map(tx.sender.toAddress -> Portfolio(-tx.fee, LeaseBalance.empty, Map.empty)),
             aliases = Map(tx.alias               -> tx.sender.toAddress)))
}
