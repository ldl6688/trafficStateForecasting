package main;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import utils.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonDemo {
    public static void main(String[] args) throws Exception {
        Map<String, List<String>> mapList = new HashMap<>();

        ArrayList<String> arrayList1 = new ArrayList<>();
        arrayList1.add("A");
        arrayList1.add("B");
        arrayList1.add("C");
        arrayList1.add("D");
        arrayList1.add("E");
        mapList.put("1108", arrayList1);

        ArrayList<String> arrayList2 = new ArrayList<>();
        arrayList2.add("Q");
        arrayList2.add("W");
        arrayList2.add("E");
        arrayList2.add("R");
        arrayList2.add("T");
        mapList.put("1109", arrayList2);

        String jsonString = JsonUtils.createJsonString(mapList);
        System.out.println(jsonString);

        //解析
        Map<String, Object> map = JsonUtils.getMapObj(jsonString);
        System.out.println(map);

        System.out.println(map.get("1108").toString());
        List<String> nameList = JSON.parseObject(map.get("1108").toString(), new TypeReference<List<String>>() {});

        System.out.println(nameList.get(3));

        //{"1109":["name":"Nick", "sex":"male"],"1108":["name": "Tom", "sex":"male"]}
        String ori = "{\"1109\":[{\"name\":\"Nick\",\"sex\":\"male\"},{\"name\":\"Tom\",\"sex\":\"male\"}],\"1108\":[{\"name\":\"Riven\",\"sex\":\"female\"},{\"name\":\"Bob\",\"sex\":\"male\"}]}";
//        {
//            "1109": [{
//            "name": "Nick",
//                    "sex": "male"
//        }, {
//            "name": "Tom",
//                    "sex": "male"
//        }],
//            "1108": [{
//            "name": "Riven",
//                    "sex": "female"
//        }, {
//            "name": "Bob",
//                    "sex": "male"
//        }]
//        }

        Map<String, Object> oriMap = JsonUtils.getMapObj(ori);
        List<Map<String, Object>> listMap = JsonUtils.getListMap(oriMap.get("1108").toString());
        String name = listMap.get(0).get("name").toString();
        System.out.println(name);
    }
}

