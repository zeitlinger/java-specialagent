/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.spring.webmvc;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.DeferredAttach;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class SpringWebMvcDeferredAttach implements DeferredAttach {
  public static final Logger logger = Logger.getLogger(SpringWebMvcDeferredAttach.class);
  public static boolean initialized;

  @Override
  public boolean isDeferrable(final Instrumentation inst) {
    try {
      Class.forName("org.springframework.context.event.ContextRefreshedEvent", false, ClassLoader.getSystemClassLoader());
      try {
        Class.forName("org.springframework.boot.SpringApplication", false, ClassLoader.getSystemClassLoader());
        return false;
      }
      catch (final ClassNotFoundException e) {
      }
    }
    catch (final ClassNotFoundException e) {
      return false;
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("\n<<<<<<<<<<<<< Installing SpringWebMvcDeferredAttach >>>>>>>>>>>>\n");

    new AgentBuilder.Default()
      .ignore(none())
      .disableClassFormatChanges()
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(hasSuperType(named("org.springframework.context.event.ContextRefreshedEvent")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(SpringWebMvcDeferredAttach.class).on(isConstructor()));
        }})
      .installOn(inst);

    if (logger.isLoggable(Level.FINE))
      logger.fine("\n>>>>>>>>>>>>> Installed SpringWebMvcDeferredAttach <<<<<<<<<<<<<\n");

    return true;
  }

  @Advice.OnMethodExit
  public static void exit(final @Advice.This Object thiz) throws ReflectiveOperationException {
    if (initialized)
      return;

    final Object applicationContext = thiz.getClass().getMethod("getSource").invoke(thiz);
    final Object parent = applicationContext.getClass().getMethod("getParent").invoke(applicationContext);
    if (parent == null) {
      initialized = true;
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n<<<<<<<<<<<<< Invoking SpringWebMvcDeferredAttach >>>>>>>>>>>>>\n");

      AgentRule.initialize();
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n<<<<<<<<<<<<< Invoked SpringWebMvcDeferredAttach >>>>>>>>>>>>>>\n");
    }
  }
}