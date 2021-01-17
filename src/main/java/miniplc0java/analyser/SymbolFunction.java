package miniplc0java.analyser;

import miniplc0java.instruction.*;
public class SymbolFunction extends Symbol{

    private Type retType;
    

    private int param_slot;
    private int loc_slot;
    private int ret_slot;

    /**the instruction list of the function*/
    public InstructionList instructionList;

    public SymbolFunction(int index_global, int index_local, int index_param) {
        super(index_global, index_local, index_param);
        this.retType = Type.VOID;
        this.instructionList = new InstructionList();
    }

    public Type getRetType() {
        return retType;
    }

    public void setRetType(Type retType) {
        this.retType = retType;
    }

    public int getParam_slot() {
        return param_slot;
    }

    public void setParam_slot(int param_slot) {
        this.param_slot = param_slot;
    }

    public int getLoc_slot() {
        return loc_slot;
    }

    public void setLoc_slot(int loc_slot) {
        this.loc_slot = loc_slot;
    }

    public int getRet_slot() {
        return ret_slot;
    }

    public void setRet_slot(int ret_slot) {
        this.ret_slot = ret_slot;
    }

    public InstructionList getInstructionList() {
        return instructionList;
    }

    @Override
    public String toString(){
        return "index_global: " + getIndex_global() +
        " index_local: " +  getIndex_local() + 
        " index_prarm: " + getIndex_param() +
        " rettype:" + retType + 
        " param_slot:" + param_slot + 
        " loc_slot:" + loc_slot +
        " ret_slot" + ret_slot +
        "\n" + instructionList.toString();

    }

}
