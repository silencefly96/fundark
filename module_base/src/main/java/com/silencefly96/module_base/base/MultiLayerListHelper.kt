package com.silencefly96.module_base.base

import androidx.arch.core.util.Function
import androidx.recyclerview.widget.RecyclerView


/**
 * 多层级列表辅助类
 * 将 ArrayList<MultiLayerNode<H, T>> 转成树结构
 * 将数据按节点形式添加、移除，适合做多级展开列表
 *
 * @author: fdk
 * @date: 2021/7/15
 */
@Suppress("unused")
object MultiLayerListHelper {

    /**
     * 将数据转成节点形式，需要设置类的前后向属性
     * @param list 原始数据
     * @param func 转换函数式接口
     * @return 节点形式列表
     */
    fun <H, T, E> transform(list: List<E>, func: Function<E, MultiLayerNode<H, T, E>>) =
        ArrayList<IMultiLayerNode<H, T, E>>().apply {
            //遍历转换
            for (peer in list) {
                func.apply(peer).let {
                    //设置说包含数据
                    it.ownEntity = peer
                    //添加到列表
                    add(it)
                }
            }
        }

    /**
     * 整理数据，使数据构成树结构，返回最顶层节点列表
     * @param list 全部数据
     * @return 最顶层节点数据列表
     */
    fun <H, T, E> sort(list: MutableList<IMultiLayerNode<H, T, E>>) =
        //添加到其他节点子列表的节点移除，最后剩下最顶层节点
        ArrayList(list).let { firstLayerNodes->
            for (peer in list) {
                for (other in list) {
                    //未被移除，即未找到前向节点，继续检查
                    if (other != peer && firstLayerNodes.contains(other)) {
                        other.hProperty?.let {
                            if (it == peer.tProperty) {
                                peer.childNodes.add(other)
                                firstLayerNodes.remove(other)
                            }
                        }
                    }
                }
            }
        }

    /**
     * 从最顶层节点数据列表获取带顺序的所有列表数据
     * @param list 最顶层节点数据列表
     * @return 带树型结构的全部数据
     */
    fun <H, T, E> toArrayList(list: MutableList<IMultiLayerNode<H, T, E>>) =
        ArrayList<IMultiLayerNode<H, T, E>>().let { nodeList ->
            //整理节点
            list.forEach { node->
                nodeList.addAll(node.toArrayList())
            }
            nodeList
        }

    /**
     * 在数据列表中移除指定节点的全部子孙节点
     * @param node 指定节点
     * @param list 全部数据
     * @return 处理后的数据
     */
    fun <H, T, E> expandNode(node: IMultiLayerNode<H, T, E>,
                             list: MutableList<IMultiLayerNode<H, T, E>>) =
        list.apply {
            if (!node.isExpand) {

                //设置节点展开
                node.isExpand = true

                //获取当前节点的位置
                val index = indexOf(node)

                //在当前位置添加节点全部子孙数据
                addAll(index + 1, node.toArrayList())
            }
        }

    /**
     * 在数据列表中移除指定节点的全部子孙节点
     * @param node 指定节点
     * @param list 全部数据
     * @return 处理后的数据
     */
    fun <H, T, E> expandNode(node: IMultiLayerNode<H, T, E>,
                             list: MutableList<IMultiLayerNode<H, T, E>>,
                             adapter: RecyclerView.Adapter<ViewHolder>) =
        list.apply {
            @Suppress("MemberVisibilityCanBePrivate")
            if (!node.isExpand) {

                //设置节点展开
                node.isExpand = true

                //获取当前节点的位置
                val index = indexOf(node)
                //获取当前节点子节点（包含递归）的数目
                val range = node.toArrayList().size

                //在当前位置添加节点全部子孙数据
                addAll(index + 1, node.toArrayList())

                //使用列表适配器范围更新
                adapter.notifyItemRangeInserted(index + 1, range)
            }
        }

    /**
     * 在数据列表中移除指定节点的全部子孙节点
     * @param node 指定节点
     * @param list 全部数据
     * @return 处理后的数据
     */
    fun <H, T, E> unExpandNode(node: IMultiLayerNode<H, T, E>,
                               list: MutableList<IMultiLayerNode<H, T, E>>) =
        list.apply {
            if (node.isExpand) {

                //获取当前节点的位置
                val index = indexOf(node)
                //获取当前节点子节点（包含递归）的数目
                val range = node.toArrayList().size

                //将全部子节点移除，从后往前，自身节点不删除
                for (tmp in (index + range) .. (index + 1)) {
                    removeAt(tmp)
                }

                //设置节点未展开
                node.isExpand = false
            }
        }

    /**
     * 在数据列表中移除指定节点的全部子孙节点,带列表适配器形式，针对范围内数据更新
     * @param node 指定节点
     * @param list 全部数据
     * @param adapter 适配器
     * @return 处理后的数据
     */
    fun <H, T, E> unExpandNode(node: MultiLayerNode<H, T, E>,
                               list: MutableList<MultiLayerNode<H, T, E>>,
                               adapter: RecyclerView.Adapter<ViewHolder>) =
        list.apply {

            //节点展开时才能移除，否则不做处理
            if (node.isExpand) {

                //获取当前节点的位置
                val index = indexOf(node)
                //获取当前节点子节点（包含递归）的数目
                val range = node.toArrayList().size

                //将全部子节点移除，从后往前，自身节点不删除
                for (tmp in (index + range) .. (index + 1)) {
                    removeAt(tmp)
                }

                //使用列表适配器范围更新
                adapter.notifyItemRangeRemoved(index + 1, index + range)

                //设置节点未展开
                node.isExpand = false
            }
        }
}

/**
 * 多层级列表节点类的接口，记得使用 setProperty 函数设置前后向属性
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
interface IMultiLayerNode<H, T, E> {

    /**
     * 前向属性
     */
    var hProperty: H?

    /**
     * 后向属性
     */
    var tProperty: T?

    /**
     * 是否展开此节点
     */
    var isExpand: Boolean

    /**
     * 生成的子列表
     */
    var childNodes: MutableList<IMultiLayerNode<H, T, E>>

    /**
     * 自定义前后想属性
     */
    fun setProperty(head: H, tail: T, entity: E) {
        hProperty = head
        tProperty = tail
    }

    /**
     * 将本节点转成列表形式
     * @return 当前节点全部子孙节点列表
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun toArrayList(): MutableList<IMultiLayerNode<H, T, E>> =
        ArrayList<IMultiLayerNode<H, T, E>>().let { nodeList ->
            //添加本节点
            nodeList.add(this)
            if (isExpand) {
                //递归添加子节点
                for (child in childNodes) {
                    nodeList.addAll(child.toArrayList())
                }
            }
            nodeList
    }
}


/**
 * 如果不想使用接口继承，可以利用上面 transform 函数，配合此类操作
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class MultiLayerNode<H, T, E>(
    override var hProperty: H? = null,
    override var tProperty: T? = null,
    override var isExpand: Boolean = false,
    override var childNodes: MutableList<IMultiLayerNode<H, T, E>> = ArrayList()
) : IMultiLayerNode<H, T, E> {

    /**
     * 携带数据
     */
    var ownEntity: E? = null
}
