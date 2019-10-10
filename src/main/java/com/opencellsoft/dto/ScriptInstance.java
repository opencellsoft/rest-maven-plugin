package com.opencellsoft.dto;

/**
 *
 * @author mohammed stitane
 */
public class ScriptInstance {
    private String code;
    private String description;
    private String type;
    private String script;

    public ScriptInstance() {
        this.setType("JAVA");
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    @Override
    public String toString() {
        return "ScriptInstance{" + "code='" + code + '\'' + ", description='" + description + '\'' + ", type='" + type + '\'' + ", script=''}";
    }
}
