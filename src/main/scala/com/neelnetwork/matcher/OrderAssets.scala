package com.neelnetwork.matcher

import java.nio.ByteBuffer

import com.neelnetwork.state.ByteStr
import com.neelnetwork.transaction.AssetId

case class OrderAssets(orderId: ByteStr, spendAsset: Option[AssetId])

object OrderAssets {
  import com.neelnetwork.matcher.util.Codecs._

  def read(b: Array[Byte]): OrderAssets = {
    val bb  = ByteBuffer.wrap(b)
    val len = bb.get()
    val arr = new Array[Byte](len)
    bb.get(arr)
    OrderAssets(ByteStr(arr), bb.getAssetId)
  }

  def write(oa: OrderAssets): Array[Byte] = {
    ByteBuffer
      .allocate(1 + oa.orderId.arr.length + len(oa.spendAsset))
      .put(oa.orderId.arr.length.toByte)
      .put(oa.orderId.arr)
      .putAssetId(oa.spendAsset)
      .array()
  }
}
