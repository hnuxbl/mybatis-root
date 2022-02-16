package org.apache.ibatis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.jdbc.SqlRunner;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author liqiang
 * created on 2022-02-13 01:00
 */
public class UtilTest {

    @Test
    public void testLog() {
        LogFactory.useStdOutLogging();
        Log log = LogFactory.getLog(UtilTest.class);
        log.debug("testLog");
        Configuration configuration;
    }

    @Test
    public void testSqlSessionBuilder() throws Exception {
        Reader reader = Resources.getResourceAsReader("mybatis-config.xml");
        SqlSessionFactoryBuilder factoryBuilder = new SqlSessionFactoryBuilder();
        SqlSessionFactory sqlSessionFactory = factoryBuilder.build(reader);
        SqlSession session = sqlSessionFactory.openSession();
        System.out.println(session);
    }

    @Test
    public void testConfigurationBuilder() throws Exception {
        Reader reader = Resources.getResourceAsReader("mybatis-config.xml");
        XMLConfigBuilder xmlConfigBuilder = new XMLConfigBuilder(reader);
        Configuration configuration = xmlConfigBuilder.parse();
        System.out.println(configuration.isMapUnderscoreToCamelCase());

    }

    @Test
    public void testXPathParser() throws Exception {
        Reader reader = Resources.getResourceAsReader("user.xml");
        XPathParser parser = new XPathParser(reader);
        List<XNode> nodeList = parser.evalNodes("/users/*");

        Field[] fields = User.class.getDeclaredFields();
        Map<String, Field> fieldMap = Arrays.stream(fields).collect(Collectors.toMap(Field::getName, a -> {
            a.setAccessible(true);
            return a;
        }));

        List<User> userList = nodeList.stream().map(xNode -> {
            User user = new User();
            Integer id = xNode.getIntAttribute("id");
            user.setId(id);
            List<XNode> children = xNode.getChildren();
            for (XNode child : children) {
                String property = child.getName();
                if (!fieldMap.containsKey(property)) {
                    continue;
                }

                try {
                    fieldMap.get(property).set(user, child.getStringBody());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            return user;
        }).collect(Collectors.toList());

        System.out.println(userList);
    }

    @Test
    public void testXPath() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputStream inputStream = Resources.getResourceAsStream("user.xml");
        Document document = builder.parse(inputStream);
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate("/users/*", document, XPathConstants.NODESET);
        List<User> userList = new ArrayList<>(nodeList.getLength());
        for (int i = 1; i < nodeList.getLength() + 1; i++) {
            String path = "/users/user[" + i + "]";
            Integer id = ((Number) xPath.evaluate(path + "/@id", document, XPathConstants.NUMBER)).intValue();
            String name = (String) xPath.evaluate(path + "/name", document, XPathConstants.STRING);
            String nickName = (String) xPath.evaluate(path + "/nickName", document, XPathConstants.STRING);
            String password = (String) xPath.evaluate(path + "/password", document, XPathConstants.STRING);
            String phone = (String) xPath.evaluate(path + "/phone", document, XPathConstants.STRING);
            String createTime = (String) xPath.evaluate(path + "/createTime", document, XPathConstants.STRING);

            User user = User.builder().id(id).name(name).nickName(nickName).phone(phone).password(password).createTime(createTime).build();
            userList.add(user);
        }

        System.out.println(userList);
    }

    @Test
    public void testInvocationHandler() {
        Animal animal = new Dog();
        Animal newAnimal = (Animal) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class<?>[]{Animal.class}, new CustomInvocationHandler(animal));
        newAnimal.walk("King");
    }

    private interface Animal {
        void walk(String flag);
    }

    private static class Dog implements Animal {
        @Override
        public void walk(String flag) {
            System.out.println("Dog.walk() with flag: " + flag);
        }
    }

    private static class CustomInvocationHandler implements InvocationHandler {
        private Object instance;

        public CustomInvocationHandler(Object instance) {
            this.instance = instance;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            System.out.println("start invoke: " + proxy.getClass().toString());
            Object object = method.invoke(instance, args);
            System.out.println("end invoke: " + object);
            return object;
        }
    }

    @Test
    public void testSqlBuilder() {
        SQL sql = new SQL() {
            {
                SELECT("P.ID, P.USERNAME, P.PASSWORD, P.FULL_NAME");
                SELECT("P.LAST_NAME, P.CREATE_TIME, P.UPDATE_TIME");
                FROM("PERSON P");
                FROM("ACCOUNT A");
                INNER_JOIN("DEPARTMENT D ON D.ID = P.DEPARTMENT_ID");
                WHERE("P.ID = A.ID");
                WHERE("P.FIRST_NAME LIKE ?");
                OR();
                WHERE("P.LAST_NAME LIKE ?");
                GROUP_BY("P.ID");
                HAVING("P.FIRST_NAME LIKE ?");
                OR();
                HAVING("P.LAST_NAME LIKE ?");
                ORDER_BY("P.ID");
                ORDER_BY("P.FULL_NAME");
            }
        };
        String newSql = sql.toString();
        System.out.println(newSql);
    }

    @Test
    public void testProxyFactory() {
        ProxyFactory proxyFactory = new JavassistProxyFactory();
        Order order = new Order("order_123", "breakfast");
        ObjectFactory objectFactory = new DefaultObjectFactory();
        Object newOrder = proxyFactory.createProxy(order, new ResultLoaderMap(), new Configuration(), objectFactory,
                Arrays.asList(String.class, String.class), Arrays.asList(order.getOrderNo(), order.getGoodsName()));

        System.out.println(newOrder.getClass());
        System.out.println(((Order) newOrder).getGoodsName());

    }

    @Test
    public void testObjectFactory() {
        ObjectFactory objectFactory = new DefaultObjectFactory();
        List<Integer> list = objectFactory.create(List.class);
        Map<String, String> map = objectFactory.create(Map.class);
        list.addAll(Arrays.asList(1, 2, 3));
        map.put("A", "1");

        System.out.println(list);
        System.out.println(map);
    }

    @Test
    public void testMetaClass() {
        MetaClass metaClass = MetaClass.forClass(Order.class, new DefaultReflectorFactory());
        String[] getterNames = metaClass.getGetterNames();
        System.out.println(Arrays.toString(getterNames));
        System.out.println("has default constructor: " + metaClass.hasDefaultConstructor());

        System.out.println("orderNo has setter: " + metaClass.hasSetter("orderNo"));
        System.out.println("orderNo has getter: " + metaClass.hasGetter("orderNo"));

        System.out.println("orderNo type: " + metaClass.getGetterType("orderNo"));

        Invoker invoker = metaClass.getGetInvoker("orderNo");
        try {
            Object orderNo = invoker.invoke(new Order("order_123", "test"), null);
            System.out.println(orderNo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMetaObject() {
        List<Order> orders = new ArrayList<>();
        orders.add(new Order("1234567", "早餐"));
        orders.add(new Order("4567899", "晚餐"));

        UserEntity userEntity = new UserEntity(orders, "Jack", 10);

        MetaObject metaObject = SystemMetaObject.forObject(userEntity);
        System.out.println(metaObject.getValue("orders[0].goodsName"));
        System.out.println(metaObject.getValue("orders[1].goodsName"));

        metaObject.setValue("orders[0].orderNo", "order_12345");

        System.out.println("has orderNo: " + metaObject.hasGetter("orderNo"));
        System.out.println("has name: " + metaObject.hasGetter("name"));
    }

    @Data
    @AllArgsConstructor
    private static class UserEntity {

        List<UtilTest.Order> orders;
        String name;
        Integer age;

    }

    @Data
    @AllArgsConstructor
    private static class Order {
        String orderNo;
        String goodsName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class User {

        private Integer id;

        private String name;

        private String nickName;

        private String password;

        private String phone;

        private String createTime;

    }
}
