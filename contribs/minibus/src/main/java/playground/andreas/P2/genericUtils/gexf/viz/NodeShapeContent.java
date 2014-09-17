//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-558 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2011.09.19 at 03:18:45 PM MESZ 
//


package playground.andreas.P2.genericUtils.gexf.viz;

import playground.andreas.P2.genericUtils.gexf.XMLSpellsContent;

import javax.xml.bind.annotation.*;


/**
 * <p>Java class for node-shape-content complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="node-shape-content">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.gexf.net/1.2draft/viz}spells" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="value" use="required" type="{http://www.gexf.net/1.2draft/viz}node-shape-type" />
 *       &lt;attribute name="uri" type="{http://www.w3.org/2001/XMLSchema}anyURI" />
 *       &lt;attribute name="start" type="{http://www.gexf.net/1.2draft}time-type" />
 *       &lt;attribute name="startopen" type="{http://www.gexf.net/1.2draft}time-type" />
 *       &lt;attribute name="end" type="{http://www.gexf.net/1.2draft}time-type" />
 *       &lt;attribute name="endopen" type="{http://www.gexf.net/1.2draft}time-type" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "node-shape-content", propOrder = {
    "spells"
})
public class NodeShapeContent {

    private XMLSpellsContent spells;
    @XmlAttribute(required = true)
    private NodeShapeType value;
    @XmlAttribute
    @XmlSchemaType(name = "anyURI")
    private String uri;
    @XmlAttribute
    private String start;
    @XmlAttribute
    private String startopen;
    @XmlAttribute
    private String end;
    @XmlAttribute
    private String endopen;

    /**
     * Gets the value of the spells property.
     * 
     * @return
     *     possible object is
     *     {@link XMLSpellsContent }
     *     
     */
    public XMLSpellsContent getSpells() {
        return spells;
    }

    /**
     * Sets the value of the spells property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLSpellsContent }
     *     
     */
    public void setSpells(XMLSpellsContent value) {
        this.spells = value;
    }

    /**
     * Gets the value of the value property.
     * 
     * @return
     *     possible object is
     *     {@link NodeShapeType }
     *     
     */
    public NodeShapeType getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     * @param value
     *     allowed object is
     *     {@link NodeShapeType }
     *     
     */
    public void setValue(NodeShapeType value) {
        this.value = value;
    }

    /**
     * Gets the value of the uri property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUri() {
        return uri;
    }

    /**
     * Sets the value of the uri property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUri(String value) {
        this.uri = value;
    }

    /**
     * Gets the value of the start property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getStart() {
        return start;
    }

    /**
     * Sets the value of the start property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setStart(String value) {
        this.start = value;
    }

    /**
     * Gets the value of the startopen property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getStartopen() {
        return startopen;
    }

    /**
     * Sets the value of the startopen property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setStartopen(String value) {
        this.startopen = value;
    }

    /**
     * Gets the value of the end property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEnd() {
        return end;
    }

    /**
     * Sets the value of the end property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEnd(String value) {
        this.end = value;
    }

    /**
     * Gets the value of the endopen property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEndopen() {
        return endopen;
    }

    /**
     * Sets the value of the endopen property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEndopen(String value) {
        this.endopen = value;
    }

}
