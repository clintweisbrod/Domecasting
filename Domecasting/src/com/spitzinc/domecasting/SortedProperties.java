package com.spitzinc.domecasting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Overrides affecting methods like store() to write key/value pairs in sorted order.
 * 
 * @author cweisbrod
 */

public class SortedProperties extends Properties
{
	private static final long serialVersionUID = 1L;
	private final List<Object> keys = new ArrayList<Object>();

    public Enumeration<Object> keys() {
    	sortKeys();
        return Collections.<Object>enumeration(keys);
    }

    public Object put(Object key, Object value) {
        keys.add(key);
        return super.put(key, value);
    }
    
    public Set<String> stringPropertyNames() {
        Set<String> set = new LinkedHashSet<String>();

        sortKeys();
        for (Object key : this.keys) {
            set.add((String)key);
        }

        return set;
    }
    
    public Set<Object> keySet() {
    	Set<Object> set = new LinkedHashSet<Object>();
    	
        sortKeys();
        for (Object key : this.keys) {
            set.add(key);
        }

        return set;
    }
    
    private void sortKeys() {
    	Collections.sort(keys, new Comparator<Object>(){
  		  public int compare(Object s1, Object s2) {
  		    return s1.toString().compareToIgnoreCase(s2.toString());
  		  }
  		});
    }
}
