package org.elasticsearch.spark.sql

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters._
import scala.collection.Map
import org.apache.spark.annotation.AlphaComponent
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.api.java.JavaRDD.fromRDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.DataFrame
import org.elasticsearch.hadoop.cfg.ConfigurationOptions.ES_QUERY
import org.elasticsearch.hadoop.cfg.ConfigurationOptions.ES_RESOURCE_READ
import org.elasticsearch.hadoop.cfg.ConfigurationOptions.ES_RESOURCE_WRITE
import org.elasticsearch.hadoop.cfg.PropertiesSettings
import org.elasticsearch.spark.cfg.SparkSettingsManager
import org.elasticsearch.hadoop.util.ObjectUtils

object EsSparkSQL {

  private val init = { ObjectUtils.loadClass("org.elasticsearch.spark.rdd.CompatUtils", classOf[ObjectUtils].getClassLoader) }

  def esDF(sc: SQLContext): DataFrame = esDF(sc, Map.empty[String, String])
  def esDF(sc: SQLContext, resource: String): DataFrame = esDF(sc, Map(ES_RESOURCE_READ -> resource))
  def esDF(sc: SQLContext, resource: String, query: String): DataFrame = esDF(sc, Map(ES_RESOURCE_READ -> resource, ES_QUERY -> query))
  def esDF(sc: SQLContext, cfg: Map[String, String]): DataFrame = {
    val esConf = new SparkSettingsManager().load(sc.sparkContext.getConf).copy();
    esConf.merge(cfg.asJava)
    
    val schema = MappingUtils.discoverMapping(esConf)
    val rowRDD = new ScalaEsRowRDD(sc.sparkContext, cfg, schema)
    sc.createDataFrame(rowRDD, schema.struct)
  }

  def esDF(sc: SQLContext, resource: String, query: String, cfg: Map[String, String]): DataFrame = {
    esDF(sc, collection.mutable.Map(cfg.toSeq: _*) += (ES_RESOURCE_READ -> resource, ES_QUERY -> query))
  }

  def esDF(sc: SQLContext, resource: String, cfg: Map[String, String]): DataFrame = {
    esDF(sc, collection.mutable.Map(cfg.toSeq: _*) += (ES_RESOURCE_READ -> resource))
  }


  def saveToEs(srdd: DataFrame, resource: String) {
    saveToEs(srdd, Map(ES_RESOURCE_WRITE -> resource))
  }
  def saveToEs(srdd: DataFrame, resource: String, cfg: Map[String, String]) {
    saveToEs(srdd, collection.mutable.Map(cfg.toSeq: _*) += (ES_RESOURCE_WRITE -> resource))
  }
  def saveToEs(srdd: DataFrame, cfg: Map[String, String]) {
    if (srdd == null || srdd.take(1).length == 0) {
      return
    }

    val sparkCtx = srdd.sqlContext.sparkContext
    val sparkCfg = new SparkSettingsManager().load(sparkCtx.getConf)
    val esCfg = new PropertiesSettings().load(sparkCfg.save())
    esCfg.merge(cfg.asJava)

    sparkCtx.runJob(srdd.rdd, new EsDataFrameWriter(srdd.schema, esCfg.save()).write _)
  }
}