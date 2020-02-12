
        /*
         * Copyright 2011 Alibaba.com All right reserved. This software is the
         * confidential and proprietary information of Alibaba.com ("Confidential
         * Information"). You shall not disclose such Confidential Information and shall
         * use it only in accordance with the terms of the license agreement you entered
         * into with Alibaba.com.
         */
        package com.alibaba.study.rpc.framework;

        import java.io.ObjectInputStream;
        import java.io.ObjectOutputStream;
        import java.lang.reflect.InvocationHandler;
        import java.lang.reflect.Method;
        import java.lang.reflect.Proxy;
        import java.net.ServerSocket;
        import java.net.Socket;

        /**
         * RpcFramework
         *
         * @author william.liangf
         */
        public class RpcFramework {

            /**
             * 暴露服务
             *
             * @param service 服务实现
             * @param port    服务端口
             * @throws Exception
             */
            public static void export(final Object service, int port) throws Exception {
                if (service == null)
                    throw new IllegalArgumentException("service instance == null");
                if (port <= 0 || port > 65535)
                    throw new IllegalArgumentException("Invalid port " + port);
                System.out.println("Export service " + service.getClass().getName() + " on port " + port);
                ServerSocket server = new ServerSocket(port);
                for (; ; ) {
                    try {
                        //相当于是服务器
                        final Socket socket = server.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    try {
                                        //从对方读数据
                                        System.out.println("测试代码是否执行到这里来1");
                                        ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                                        try {
                                            String methodName = input.readUTF(); //方法名字
                                            Class<?>[] parameterTypes = (Class<?>[]) input.readObject(); //方法参数的类型
                                            Object[] arguments = (Object[]) input.readObject(); //方法的参数，是数组，因为可能有多个参数

                                            //写数据到对方
                                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                                            try {
                                                Method method = service.getClass().getMethod(methodName, parameterTypes);
                                                Object result = method.invoke(service, arguments); //调用实现类的方法。具体是基于反射来调用实现类的方法。//这里为什么不直接调用，而是基于反射去调用？
                                                output.writeObject(result); //写数据到对方
                                            } catch (Throwable t) {
                                                output.writeObject(t);
                                            } finally {
                                                output.close();
                                            }
                                        } finally {
                                            input.close();
                                        }
                                    } finally {
                                        socket.close();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            /**
             * 引用服务
             *
             * @param <T>            接口泛型
             * @param interfaceClass 接口类型
             * @param host           服务器主机名
             * @param port           服务器端口
             * @return 远程服务
             * @throws Exception
             */
            @SuppressWarnings("unchecked")
            public static <T> T refer(final Class<T> interfaceClass, final String host, final int port) throws Exception {
                if (interfaceClass == null)
                    throw new IllegalArgumentException("Interface class == null");
                if (!interfaceClass.isInterface())
                    throw new IllegalArgumentException("The " + interfaceClass.getName() + " must be interface class!");
                if (host == null || host.length() == 0)
                    throw new IllegalArgumentException("Host == null!");
                if (port <= 0 || port > 65535)
                    throw new IllegalArgumentException("Invalid port " + port);
                System.out.println("Get remote service " + interfaceClass.getName() + " from server " + host + ":" + port);

                /**
                 * 相当于是客户端。
                 *
                 * 代理的作用是，回调。具体来说是，只要调用了
                 *
                 */
                return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[]{interfaceClass}, new InvocationHandler() {
                    /**
                     * 客户端每次调用远程服务方法的时候，就会回调这里。本质是因为在这里读写数据，和服务器进行通信。
                     * 通信的底层实现，其实就是基于socket，没什么新鲜的东西。
                     */
                     public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
                        Socket socket = new Socket(host, port);
                        try {
                            //写数据到对方
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                            try {
                                output.writeUTF(method.getName());
                                output.writeObject(method.getParameterTypes());
                                output.writeObject(arguments);

                                //从对方读数据
                                ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                                try {
                                    Object result = input.readObject();
                                    if (result instanceof Throwable) {
                                        throw (Throwable) result;
                                    }
                                    return result;
                                } finally {
                                    input.close();
                                }
                            } finally {
                                output.close();
                            }
                        } finally {
                            socket.close();
                        }
                    }
                });
            }

        }