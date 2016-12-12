package com.github.vendigo.acemybatis.method.insert;

import com.github.vendigo.acemybatis.config.AceConfig;
import com.github.vendigo.acemybatis.method.change.ChangeMethod;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public class AsyncInsert extends ChangeMethod {

    public AsyncInsert(Method method, MapperMethod.MethodSignature methodSignature, AceConfig config) {
        super(method, methodSignature, config);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<Integer> execute(SqlSessionFactory sqlSessionFactory, Object[] args) throws Exception {
        return doExecute(SqlSession::insert, sqlSessionFactory, args);
    }
}
