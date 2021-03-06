package com.neelnetwork.it.sync.matcher

import com.typesafe.config.Config
import com.neelnetwork.account.PrivateKeyAccount
import com.neelnetwork.it.api.SyncHttpApi._
import com.neelnetwork.it.api.SyncMatcherHttpApi._
import com.neelnetwork.it.matcher.MatcherSuiteBase
import com.neelnetwork.it.sync._
import com.neelnetwork.it.sync.matcher.config.MatcherPriceAssetConfig._
import com.neelnetwork.transaction.assets.exchange.Order.PriceConstant
import com.neelnetwork.transaction.assets.exchange.OrderType._

class OrderBookTestSuite extends MatcherSuiteBase {

  override protected def nodeConfigs: Seq[Config] = Configs

  Seq(IssueUsdTx, IssueWctTx).map(createSignedIssueRequest).map(matcherNode.signedIssue).foreach { tx =>
    matcherNode.waitForTransaction(tx.id)
  }

  Seq(
    aliceNode.transfer(IssueUsdTx.sender.toAddress.stringRepr, aliceAcc.address, defaultAssetQuantity, 100000, Some(UsdId.toString), None, 2),
    bobNode.transfer(IssueWctTx.sender.toAddress.stringRepr, bobAcc.address, defaultAssetQuantity, 100000, Some(WctId.toString), None, 2)
  ).foreach { tx =>
    matcherNode.waitForTransaction(tx.id)
  }

  case class ReservedBalances(wct: Long, usd: Long, neel: Long)
  def reservedBalancesOf(pk: PrivateKeyAccount): ReservedBalances = {
    val reservedBalances = matcherNode.reservedBalance(pk)
    ReservedBalances(
      reservedBalances.getOrElse(WctId.toString, 0),
      reservedBalances.getOrElse(UsdId.toString, 0),
      reservedBalances.getOrElse("NEEL", 0)
    )
  }

  val (amount, price) = (1000L, PriceConstant)

  "When delete order book" - {
    val buyOrder        = matcherNode.placeOrder(aliceAcc, wctUsdPair, BUY, 2 * amount, price, matcherFee).message.id
    val anotherBuyOrder = matcherNode.placeOrder(aliceAcc, wctUsdPair, BUY, amount, price, matcherFee).message.id

    val submitted = matcherNode.placeOrder(bobAcc, wctUsdPair, SELL, amount, price, matcherFee).message.id

    val sellOrder = matcherNode.placeOrder(bobAcc, wctUsdPair, SELL, amount, 2 * price, matcherFee).message.id

    matcherNode.waitOrderStatus(wctUsdPair, buyOrder, "PartiallyFilled")
    matcherNode.waitOrderStatus(wctUsdPair, submitted, "Filled")

    val (aliceRBForOnePair, bobRBForOnePair) = (reservedBalancesOf(aliceAcc), reservedBalancesOf(bobAcc))

    val buyOrderForAnotherPair = matcherNode.placeOrder(aliceAcc, wctNeelPair, BUY, amount, price, matcherFee).message.id
    val sellOrderForAnotherPair =
      matcherNode.placeOrder(bobAcc, wctNeelPair, SELL, amount, 2 * price, matcherFee).message.id

    matcherNode.waitOrderStatus(wctNeelPair, buyOrderForAnotherPair, "Accepted")
    matcherNode.waitOrderStatus(wctNeelPair, sellOrderForAnotherPair, "Accepted")

    val (aliceRBForBothPairs, bobRBForBothPairs) = (reservedBalancesOf(aliceAcc), reservedBalancesOf(bobAcc))

    val marketStatusBeforeDeletion = matcherNode.marketStatus(wctUsdPair)

    matcherNode.deleteOrderBook(wctUsdPair)

    "orders by the pair should be canceled" in {
      matcherNode.waitOrderStatus(wctUsdPair, buyOrder, "Cancelled")
      matcherNode.waitOrderStatus(wctUsdPair, anotherBuyOrder, "Cancelled")
      matcherNode.waitOrderStatus(wctUsdPair, sellOrder, "Cancelled")
    }

    "orderbook was really deleted" in {
      val orderBook = matcherNode.orderBook(wctUsdPair)
      orderBook.bids shouldBe empty
      orderBook.asks shouldBe empty
    }

    "reserved balances should be released for the pair" in {
      val (aliceReservedBalances, bobReservedBalances) = (reservedBalancesOf(aliceAcc), reservedBalancesOf(bobAcc))
      aliceReservedBalances.usd shouldBe 0
      aliceReservedBalances.neel shouldBe (aliceRBForBothPairs.neel - aliceRBForOnePair.neel)
      bobReservedBalances.wct shouldBe (bobRBForBothPairs.wct - bobRBForOnePair.wct)
      bobReservedBalances.neel shouldBe (bobRBForBothPairs.neel - bobRBForOnePair.neel)
    }

    "it should not affect other pairs and their orders" in {
      matcherNode.orderStatus(buyOrderForAnotherPair, wctNeelPair).status shouldBe "Accepted"
      matcherNode.orderStatus(sellOrderForAnotherPair, wctNeelPair).status shouldBe "Accepted"

      val orderBook = matcherNode.orderBook(wctNeelPair)
      orderBook.bids shouldNot be(empty)
      orderBook.asks shouldNot be(empty)
    }

    "it should not affect market status" in {
      matcherNode.marketStatus(wctUsdPair) shouldEqual marketStatusBeforeDeletion
    }
  }

}
