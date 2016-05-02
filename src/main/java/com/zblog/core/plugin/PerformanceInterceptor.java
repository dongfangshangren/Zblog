package com.zblog.core.plugin;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zblog.core.util.DateUtils;

/**
 * MyBatis 性能拦截器，用于输出每条 SQL 语句及其执行时间
 * 
 * @author zhou
 *
 */
@Intercepts({
    @Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class, RowBounds.class,
        ResultHandler.class }),
    @Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class }) })
public class PerformanceInterceptor implements Interceptor{
  private static final Logger logger = LoggerFactory.getLogger(PerformanceInterceptor.class);

  @Override
  public Object intercept(Invocation invocation) throws Throwable{
    MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
    Object parameter = null;
    if(invocation.getArgs().length > 1){
      parameter = invocation.getArgs()[1];
    }

    String statementId = mappedStatement.getId();
    BoundSql boundSql = mappedStatement.getBoundSql(parameter);
    Configuration configuration = mappedStatement.getConfiguration();
    //String sql = getSql(boundSql, parameter, configuration);
    String sql = showSql(configuration, boundSql) ;
    long start = System.currentTimeMillis();
    Object result = invocation.proceed();
    long timing = System.currentTimeMillis() - start;

    logger.debug("use time->" + timing + " ms" + " - id:" + statementId + " - sql:" + sql);
    return result;
  }

  @Override
  public Object plugin(Object target){
    /* 当目标类是Executor类型时，才包装目标类，不做无谓的代理 */
    return (target instanceof Executor) ? Plugin.wrap(target, this) : target;
  }

  @Override
  public void setProperties(Properties properties){
  }
  
  
  
  private String getSql(BoundSql boundSql, Object parameterObject, Configuration configuration){
    String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    if(parameterMappings != null){
      for(int i = 0; i < parameterMappings.size(); i++){
        ParameterMapping parameterMapping = parameterMappings.get(i);
        if(parameterMapping.getMode() != ParameterMode.OUT){
          Object value;
          String propertyName = parameterMapping.getProperty();
          if(boundSql.hasAdditionalParameter(propertyName)){
            value = boundSql.getAdditionalParameter(propertyName);
          }else if(parameterObject == null){
            value = null;
          }else if(typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())){
            value = parameterObject;
          }else{
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }
          sql = replace(sql, value);
        }
      }
    }

    return sql;
  }

  private String replace(String sql, Object propertyValue){
    String result = "null";
    if(propertyValue != null){
      if(propertyValue instanceof String){
        result = "'" + propertyValue + "'";
      }else if(propertyValue instanceof Date){
        result = "'" + DateUtils.formatDate("yyyy-MM-dd HH:mm:ss", (Date) propertyValue) + "'";
      }else{
        result = propertyValue.toString();
      }
    }
    return sql.replaceFirst("\\?", result);
  }
  
  
  
  private static String getParameterValue(Object obj) {
      String value = null;
      if (obj instanceof String) {
          value = "'" + obj.toString() + "'";
      } else if (obj instanceof Date) {
          DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.CHINA);
          value = "'" + formatter.format(new Date()) + "'";
      } else {
          if (obj != null) {
        	  
              value = obj.toString();
          } else {
              value = "";
          }

      }
      return value;
  }

  public static String showSql(Configuration configuration, BoundSql boundSql) {
      Object parameterObject = boundSql.getParameterObject();
      List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
      String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
      if (parameterMappings.size() > 0 && parameterObject != null) {
          TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
          if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
              sql = sql.replaceFirst("\\?", getParameterValue(parameterObject));

          } else {
              MetaObject metaObject = configuration.newMetaObject(parameterObject);
              for (ParameterMapping parameterMapping : parameterMappings) {
                  String propertyName = parameterMapping.getProperty();
                  if (metaObject.hasGetter(propertyName)) {
                      Object obj = metaObject.getValue(propertyName);
                      //sql = sql.replaceFirst("\\,*\\s*\\?", getParameterValue(obj));
                      // 实用jdk原生方法replaceFirst部分情况下出现异常：named capturing group has 0 length name
                      // 改用 org.apache.commons.lang3.StringUtils 代替
                      sql = StringUtils.replaceOnce(sql, "\\?", getParameterValue(obj));
                  } else if (boundSql.hasAdditionalParameter(propertyName)) {
                      Object obj = boundSql.getAdditionalParameter(propertyName);
                      //sql = sql.replaceFirst("\\?", getParameterValue(obj));
                      sql = StringUtils.replaceOnce(sql, "\\?", getParameterValue(obj));
                  }
              }
          }
      }
      return sql;
  }

}
