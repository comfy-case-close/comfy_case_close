package com.fnbx.hrm.service.contractdoc.docx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

final class WordXml {

    private static final String NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final List<String> DISABLED_VALUES = List.of("false", "0", "off");

    private WordXml() {
    }

    static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && NAMESPACE.equals(element.getNamespaceURI()) && name.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && NAMESPACE.equals(element.getNamespaceURI())) {
                elements.add(element);
            }
        }
        return elements;
    }

    static List<Element> children(Element parent, String name) {
        return children(parent).stream().filter(element -> element.getLocalName().equals(name)).toList();
    }

    static boolean has(Element parent, String name) {
        return child(parent, name) != null;
    }

    static String attribute(Element element, String name) {
        if (element == null) {
            return null;
        }
        String value = element.getAttributeNS(NAMESPACE, name);
        return value.isEmpty() ? null : value;
    }

    static boolean isSwitchedOn(Element properties, String name) {
        Element element = child(properties, name);
        if (element == null) {
            return false;
        }
        String value = attribute(element, "val");
        return value == null || !DISABLED_VALUES.contains(value.toLowerCase(Locale.ROOT));
    }
}
