package miniplc0java.analyser;

public class SymbolVar extends Symbol{
    
    private boolean isGlobal;
    private boolean isConstant;
    private boolean isParam;
    
    private Type type;
    
    public SymbolVar(int index_global, int index_local, int index_param){
        super(index_global, index_local, index_param);
        this.isConstant = this.isGlobal = this.isParam = false;
        type = Type.VOID;
    }

    @Override
    public String toString(){
        return "is Constant: " + isConstant + " is Global:" + isGlobal + 
        " is Param:" + isParam +
        " datatype:" + type + "\n";
    }

    public boolean isConstant() {
        return isConstant;
    }

    public void setConstant(boolean isConstant) {
        this.isConstant = isConstant;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public void setGlobal(boolean isGlobal) {
        this.isGlobal = isGlobal;
    }

    public Type getDataType() {
        return type;
    }

    public void setDataType(Type type) {
        this.type = type;
    }

    public boolean isParam() {
        return isParam;
    }

    public void setParam(boolean isParam) {
        this.isParam = isParam;
    }

}
