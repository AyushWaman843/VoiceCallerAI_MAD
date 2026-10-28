package com.ayush.madv2.ui

import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.http.Body
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

class BusinessApiSignatureTest {
    @Test
    fun requestBodiesHaveNoWildcardsForRetrofit() {
        BusinessApi::class.java.declaredMethods.forEach { method ->
            method.parameterAnnotations.forEachIndexed { index, annotations ->
                if (annotations.any { it is Body }) {
                    val bodyType = method.genericParameterTypes[index] as ParameterizedType
                    assertFalse("${method.name} has a wildcard request body",
                        bodyType.actualTypeArguments.any { it is WildcardType })
                }
            }
        }
    }
}
