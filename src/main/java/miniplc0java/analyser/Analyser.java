package miniplc0java.analyser;

import java.util.Map;

import miniplc0java.error.*;
import miniplc0java.instruction.*;
import miniplc0java.tokenizer.*;
import miniplc0java.util.Pos;

public final class Analyser {

    Tokenizer tokenizer;
//    ArrayList<Instruction> instructions;

    /** 当前偷看的 token */
    Token peekedToken = null;

    /** 符号表 */
    public SymbolTableList symbolTableList;

    int level;
    int op;
    int loc_slot, param_slot, ret_slot;

    SymbolFunction symbolFunction;
    SymbolTable symbolTable;

    int breakins, continueins;

    boolean hasRet;

    public Analyser(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.level = 0;
        this.symbolTableList = new SymbolTableList();
        this.symbolFunction = (SymbolFunction)symbolTableList.getFunctionSymbolTable().get("_start");
        this.symbolTable = new SymbolTable(0);
        this.op = 0;
        this.hasRet = false;
    }

    /**
     * 查看下一个 Token
     *
     * @return
     * @throws TokenizeError
     */
    private Token peek() throws TokenizeError {
        if (peekedToken == null) {
            peekedToken = tokenizer.nextToken();
        }
        return peekedToken;
    }

    /**
     * 获取下一个 Token
     *
     * @return
     * @throws TokenizeError
     */
    private Token next() throws TokenizeError {
        op++;
        if (peekedToken != null) {
            var token = peekedToken;
            peekedToken = null;
            return token;
        } else {
            return tokenizer.nextToken();
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则返回 true
     *
     * @param tt
     * @return
     * @throws TokenizeError
     */
    private boolean check(TokenType tt) throws TokenizeError {
        var token = peek();
        return token.getTokenType() == tt;
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回这个 token
     *
     * @param tt 类型
     * @return 如果匹配则返回这个 token，否则返回 null
     * @throws TokenizeError
     */
    private Token nextIf(TokenType tt) throws TokenizeError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            return null;
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回，否则抛出异常
     *
     * @param tt 类型
     * @return 这个 token
     * @throws CompileError 如果类型不匹配
     */
    private Token expect(TokenType tt) throws CompileError {
        op++;
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            throw new ExpectedTokenError(tt, token);
        }
    }

    /**
     * program -> decl_stmt* function*
     * @throws CompileError
     */
    public void analyseProgram() throws CompileError{

        boolean eof = true;
        while(eof)
        {
            switch(peek().getTokenType()){
        
            case LET_KW:
            case CONST_KW:
                analyseDeclareStatement();
                break;
            case FN_KW:
                analyseFunction();
                break;
            default:
                if(peek().getTokenType() == TokenType.EOF)
                    eof = false;
                else
                    throw new AnalyzeError(ErrorCode.ExpectedToken, peek().getStartPos());   
            }
        }

        SymbolFunction main = symbolTableList.searchSymbolFn("main");
        if(main == null)
            throw new AnalyzeError(ErrorCode.NullMainFunction, new Pos(0, 0));

        InstructionList start = symbolTableList.searchSymbolFn("_start").getInstructionList();
        start.add(new Instruction(InstructionType.stackAlloc, true, main.getRet_slot()));
        start.add(new Instruction(InstructionType.call, true, main.getIndex_global()));
        if(main.getRet_slot() != 0)
            start.add(new Instruction(InstructionType.popN, true, main.getRet_slot()));

       symbolTableList.print();
             
    }

    public void analyseFunction() throws CompileError{
        expect(TokenType.FN_KW);
        symbolFunction = new SymbolFunction(symbolTableList.getFunctionSymbolTable().size(), symbolTableList.size(), -1);
        symbolTable = new SymbolTable(0);
        ret_slot = loc_slot = param_slot = 0;
        hasRet = false;
        breakins = continueins = -1;

        Token name, type;

        name = expect(TokenType.IDENT);
        if(name.getDataType() != Type.NONE)
            throw new AnalyzeError(ErrorCode.InvalidType, name.getStartPos());
        if(symbolTableList.searchSymbolFn(name.getValueString()) != null)
            throw new AnalyzeError(ErrorCode.DuplicateDeclaration, name.getStartPos());
        expect(TokenType.L_PAREN);
        if(peek().getTokenType() != TokenType.R_PAREN){
            analyseFunction_param();
            while(peek().getTokenType() == TokenType.COMMA){
                next();
                analyseFunction_param();
            }
        }
        symbolFunction.setParam_slot(param_slot);
        expect(TokenType.R_PAREN);
        expect(TokenType.ARROW);
        type = expect(TokenType.IDENT);
        if(type.getDataType() == Type.NONE)
            throw new AnalyzeError(ErrorCode.InvalidType, type.getStartPos());
        symbolFunction.setRetType(type.getDataType());
        if(type.getDataType() == Type.VOID)
            ret_slot = 0;
        else
            ret_slot = 1;
        symbolFunction.setRet_slot(ret_slot);

        symbolTableList.getFunctionSymbolTable().put(name.getValueString(), symbolFunction);
        analyseBlock_stmt();
        symbolFunction.setLoc_slot(loc_slot);

        if(!hasRet && symbolFunction.getRetType() == Type.VOID)
            symbolFunction.instructionList.add(new Instruction(InstructionType.ret));
        else if(!hasRet)
            throw new AnalyzeError(ErrorCode.NotAllRoutesReturn, name.getStartPos());

        symbolFunction = (SymbolFunction)symbolTableList.getFunctionSymbolTable().get("_start");
    }

    private void analyseFunction_param_list() throws CompileError{
        analyseFunction_param();
        while(nextIf(TokenType.COMMA) != null){
            analyseFunction_param();
        }
    }

    private void analyseFunction_param() throws CompileError{
        Token name, type;
        SymbolVar var = new SymbolVar(-1, -1, param_slot);

        if(peek().getTokenType() == TokenType.CONST_KW){
            var.setConstant(true);
            next();
        }

        name = expect(TokenType.IDENT);
        if(name.getDataType() == Type.UINT || name.getDataType() == Type.DOUBLE)
            throw new AnalyzeError(ErrorCode.InvalidType, name.getStartPos());

        expect(TokenType.COLON);

        type = expect(TokenType.IDENT);
        if(type.getDataType() == Type.NONE || name.getDataType() == Type.VOID)
            throw new AnalyzeError(ErrorCode.InvalidType, type.getStartPos());
        var.setDataType(type.getDataType());

        var.setParam(true);
        symbolTable.put(name.getValueString(), var);
        param_slot ++;
    }

    private void analyseStatement() throws CompileError{
        
        switch(peek().getTokenType()){
        case LET_KW:
        case CONST_KW:
            analyseDeclareStatement();
            break;
        case IF_KW:
            analyseIf_stmt();
            break;
        case WHILE_KW:
            analyseWhile_stmt();
            break;
        case RETURN_KW:
            analyseReturn_stmt();
            break;
        case SEMICOLON:
            next();
            break;
        case L_BRACE:
            analyseBlock_stmt();
            break;
        case BREAK_KW:
            analyseBreak_stmt();
            break;
        case CONTINUE_KW:
            analyseContinue_stmt();
            break;
        default:
            if(!analyseExpr())
                symbolFunction.instructionList.add(new Instruction(InstructionType.popN, true, 1));
            expect(TokenType.SEMICOLON);
        }
    }

    private void analyseBlock_stmt() throws CompileError{
        expect(TokenType.L_BRACE);
        level++;
        symbolTableList.addSymbolTable(level);
        symbolTableList.getLocalSymbolTable(symbolFunction).cat(symbolTable);
        symbolTable.clear();
        boolean isStatement = true;
        while(isStatement){
            switch(peek().getTokenType()){
            case R_BRACE:
                isStatement = false;
                break;
            default:
                analyseStatement();
            }
        }
        expect(TokenType.R_BRACE);
        level--;
        if(level != 0)symbolTableList.popSymbolTable();
    }

    private void analyseDeclareStatement() throws CompileError{

        if(peek().getTokenType() != TokenType.LET_KW && peek().getTokenType() != TokenType.CONST_KW)
            throw new AnalyzeError(ErrorCode.ExpectedToken, peek().getStartPos());

        Token name, type;

        SymbolVar var = new SymbolVar(symbolTableList.getGlobalSymbolTable().size(), loc_slot, param_slot);

        if(next().getTokenType() == TokenType.LET_KW)
            var.setConstant(false);
        else
            var.setConstant(true);        

        var.setGlobal(level == 0);

        name = expect(TokenType.IDENT);
        if(name.getDataType() == Type.UINT || name.getDataType() == Type.DOUBLE)
            throw new AnalyzeError(ErrorCode.InvalidType, name.getStartPos());

        expect(TokenType.COLON);

        type = expect(TokenType.IDENT);
        if(type.getDataType() == Type.NONE || type.getDataType() == Type.VOID)
            throw new AnalyzeError(ErrorCode.InvalidType, type.getStartPos());
        var.setDataType(type.getDataType());

        if(symbolTableList.searchDuplicatedSymbolVar(name.getValueString()) != null)
            throw new AnalyzeError(ErrorCode.DuplicateDeclaration, name.getStartPos());

        if(peek().getTokenType() != TokenType.ASSIGN && var.isConstant())
            throw new AnalyzeError(ErrorCode.ConstantNeedValue, peek().getStartPos());

        if(level == 0)
            symbolTableList.getGlobalSymbolTable().put(name.getValueString(), var);
        else{
            symbolTableList.getCurrentSymbolTable().put(name.getValueString(), var);
            loc_slot ++;
        }

        if(peek().getTokenType() == TokenType.ASSIGN){
            next();

            if(level == 0)
                symbolFunction.instructionList.add(new Instruction(InstructionType.globA, true, var.getIndex_global()));
            else
                symbolFunction.instructionList.add(new Instruction(InstructionType.locA, true, var.getIndex_local()));
            if(var.getDataType() != analyseExpr_b())
                throw new AnalyzeError(ErrorCode.TypeMismatch, name.getStartPos());
            symbolFunction.instructionList.add(new Instruction(InstructionType.store64));
        }

        expect(TokenType.SEMICOLON);
    }

    private void analyseIf_stmt() throws CompileError{
        boolean temp = hasRet;
        boolean ifhasRet = true;
        hasRet = false;

        expect(TokenType.IF_KW);
        analyseExpr_b();

        symbolFunction.instructionList.add(new Instruction(InstructionType.brTrue, true, 1));
        symbolFunction.instructionList.add(new Instruction(InstructionType.br, true, 0));
        int ifins = symbolFunction.instructionList.size();

        analyseBlock_stmt();
        symbolFunction.instructionList.add(new Instruction(InstructionType.br, true, 0));
        symbolFunction.instructionList.get(ifins - 1).setNum_32(symbolFunction.instructionList.size() - ifins);

        if(!hasRet)ifhasRet = false;
        hasRet = false;
        //else
        if(peek().getTokenType() == TokenType.ELSE_KW){
            int elseins = symbolFunction.instructionList.size();

            //get 'else'
            next();

            if(peek().getTokenType() == TokenType.IF_KW)
                analyseIf_stmt();
            else
                analyseBlock_stmt();

            symbolFunction.instructionList.get(elseins - 1).setNum_32(symbolFunction.instructionList.size() - elseins);
        }
        if(!hasRet)ifhasRet = false;

        hasRet = temp || ifhasRet;
    }

    private void analyseWhile_stmt() throws CompileError{
        boolean temp = hasRet;
        int tempbreakins = breakins;
        int tempcontinueins = continueins;
        expect(TokenType.WHILE_KW);

        continueins = symbolFunction.instructionList.size();
        analyseExpr_b();

        symbolFunction.instructionList.add(new Instruction(InstructionType.brTrue, true, 1));
        breakins = symbolFunction.instructionList.size();
        symbolFunction.instructionList.add(new Instruction(InstructionType.br, true, 0));
        int whileins = symbolFunction.instructionList.size();

        analyseBlock_stmt();

        symbolFunction.instructionList.add(new Instruction(InstructionType.br, true, 0));
        symbolFunction.instructionList.get(symbolFunction.instructionList.size() - 1).setNum_32(continueins - symbolFunction.instructionList.size());
        symbolFunction.instructionList.get(whileins - 1).setNum_32(symbolFunction.instructionList.size() - whileins);
        breakins = tempbreakins;
        continueins = tempcontinueins;
        hasRet = temp;
    }

    private void analyseBreak_stmt() throws CompileError{
        expect(TokenType.BREAK_KW);
        if(continueins == -1)throw new AnalyzeError(ErrorCode.NoBreakContext, peek().getStartPos());
        symbolFunction.instructionList.add(new Instruction(InstructionType.br, true, 0));
        symbolFunction.instructionList.get(symbolFunction.instructionList.size() - 1).setNum_32(breakins - symbolFunction.instructionList.size());
        expect(TokenType.SEMICOLON);
    }

    private void analyseContinue_stmt() throws CompileError{
        expect(TokenType.CONTINUE_KW);
        if(continueins == -1)throw new AnalyzeError(ErrorCode.NoContinueContext, peek().getStartPos());
        symbolFunction.instructionList.add(new Instruction(InstructionType.br, true, 0));
        symbolFunction.instructionList.get(symbolFunction.instructionList.size() - 1).setNum_32(continueins - symbolFunction.instructionList.size());
        expect(TokenType.SEMICOLON);
    }

    private void analyseReturn_stmt() throws CompileError{
        Type retType;
        hasRet = true;
        Token ret = expect(TokenType.RETURN_KW);
        if(peek().getTokenType() != TokenType.SEMICOLON){
            symbolFunction.instructionList.add(new Instruction(InstructionType.argA, 0));
            retType = analyseExpr_b();
            symbolFunction.instructionList.add(new Instruction(InstructionType.store64));
        }
        else{
            retType = Type.VOID;
            next();
        }
        if(retType != symbolFunction.getRetType())
            throw new AnalyzeError(ErrorCode.InvalidReturn, ret.getStartPos());
        symbolFunction.instructionList.add(new Instruction(InstructionType.ret));
    }

    private boolean analyseExpr() throws CompileError {
        op = 0;
        Token peeked = peek();
        
        Type retType = analyseExpr_b();

        if (peek().getTokenType() == TokenType.ASSIGN) {
            if(peeked.getTokenType() != TokenType.IDENT || op != 1)
                throw new AnalyzeError(ErrorCode.ExpectedToken, peek().getStartPos());

            symbolFunction.instructionList.pop();

            Symbol sym = symbolTableList.searchSymbol(peeked.getValueString());

            if(sym == null || sym instanceof SymbolFunction)
                throw new AnalyzeError(ErrorCode.NotDeclared, peeked.getStartPos());
            
            SymbolVar var = (SymbolVar)sym;

            if(var.isConstant())
                throw new AnalyzeError(ErrorCode.AssignToConstant, peek().getStartPos());
            next();

            if(var.getDataType() != analyseExpr_b())
                throw new AnalyzeError(ErrorCode.TypeMismatch, peeked.getStartPos());

            symbolFunction.instructionList.add(new Instruction(InstructionType.store64));
            return true;
        }
        if(retType == Type.UINT || retType == Type.DOUBLE)
            return false;
        return true;
        /*        // negate_expr
        if(peeked.getTokenType() == TokenType.MINUS){
            analyseNegate_expr();
        }
        // ident_expr
        else if(peeked.getTokenType() == TokenType.IDENT){
            next();
            // assign_expr
            if(nextIf(TokenType.ASSIGN) != null){
                analyseExpr();
            }
            // call_expr
            else if(nextIf(TokenType.L_PAREN) != null){
                if(peek().getTokenType() != TokenType.R_PAREN){
                    analyseCall_param_list();
                }
                expect(TokenType.R_PAREN);
            }
        }
        // literal_expr
        else if(peeked.getTokenType() == TokenType.UINT_LITERAL || peeked.getTokenType() == TokenType.DOUBLE_LITERAL || peeked .getTokenType() == TokenType.STRING_LITERAL){
            next();
        }
        // group_expr
        else if(peeked.getTokenType() == TokenType.L_PAREN){
            analyseGroup_expr();
        }
        else{
            throw new ExpectedTokenError(List.of(TokenType.MINUS, TokenType.IDENT, TokenType.UINT_LITERAL, TokenType.STRING_LITERAL, TokenType.L_PAREN), next());
        }
        while (true){
            peeked = peek();
            // operator_expr
            if(peeked.getTokenType() == TokenType.PLUS || peeked.getTokenType() == TokenType.MINUS
                    || peeked.getTokenType() == TokenType.MUL || peeked.getTokenType() == TokenType.DIV
                    || peeked.getTokenType() == TokenType.EQ || peeked.getTokenType() == TokenType.NEQ
                    || peeked.getTokenType() == TokenType.LT || peeked.getTokenType() == TokenType.GT
                    || peeked.getTokenType() == TokenType.LE || peeked.getTokenType() == TokenType.GE){
                next();
                analyseExpr();
            }
            // as_expr
            else if(peeked.getTokenType() == TokenType.AS_KW){
                analyseAs_expr();
            }
            else{
                break;
            }
        }*/
    }

    private Type analyseExpr_b() throws CompileError {
        
        Type left = analyseExpr_c();
        Type right;
        Token oper;
        switch (peek().getTokenType()) {
        case EQ:case NEQ:case LT:case GT:case LE:case GE:
            oper = next();
            right = analyseExpr_c();
            break;
        default:
            return left;
        }
        if(left != right)
            throw new AnalyzeError(ErrorCode.TypeMismatch, oper.getStartPos());

        switch(left){
            case UINT:
                symbolFunction.instructionList.add(new Instruction(InstructionType.cmpI));
                break;
            case DOUBLE:
                symbolFunction.instructionList.add(new Instruction(InstructionType.cmpF));
                break;
            default:
                throw new AnalyzeError(ErrorCode.TypeMismatch, oper.getStartPos());
                
        }
       
        switch (oper.getTokenType()){
        case EQ:
            symbolFunction.instructionList.add(new Instruction(InstructionType.not));
            break;
        case NEQ:
            break;
        case LT:
            symbolFunction.instructionList.add(new Instruction(InstructionType.setLt));
            break;
        case GT:
            symbolFunction.instructionList.add(new Instruction(InstructionType.setGt));
            break;
        case LE:
            symbolFunction.instructionList.add(new Instruction(InstructionType.setGt));
            symbolFunction.instructionList.add(new Instruction(InstructionType.not));
            break;
        case GE:
            symbolFunction.instructionList.add(new Instruction(InstructionType.setLt));
            symbolFunction.instructionList.add(new Instruction(InstructionType.not));
            break;

        default:
        }
        return left;
    }

    private Type analyseExpr_c() throws CompileError {
        Type left = analyseExpr_d();
        Type right;
        Token oper;
        while (peek().getTokenType() == TokenType.PLUS || peek().getTokenType() == TokenType.MINUS) {
            oper = next();
            right = analyseExpr_d();

            if(left != right)
                throw new AnalyzeError(ErrorCode.TypeMismatch, oper.getStartPos());
            
            switch(right){
            case DOUBLE:
                if(oper.getTokenType() == TokenType.PLUS)
                    symbolFunction.instructionList.add(new Instruction(InstructionType.addF));
                else
                    symbolFunction.instructionList.add(new Instruction(InstructionType.subF));
                break;
            case UINT:
                if(oper.getTokenType() == TokenType.PLUS)
                    symbolFunction.instructionList.add(new Instruction(InstructionType.addI));
                else
                    symbolFunction.instructionList.add(new Instruction(InstructionType.subI));
                break;
            default:
                throw new AnalyzeError(ErrorCode.TypeMismatch, oper.getStartPos());
            }

        }
        return left;
    }

    private Type analyseExpr_d() throws CompileError {
        Type left = analyseExpr_e();
        Type right;
        Token oper;
        while (peek().getTokenType() == TokenType.MUL || peek().getTokenType() == TokenType.DIV) {
            oper = next();
            right = analyseExpr_e();

            if(left != right)
                throw new AnalyzeError(ErrorCode.TypeMismatch, oper.getStartPos());
            
            switch(right){
            case DOUBLE:
                if(oper.getTokenType() == TokenType.MUL)
                    symbolFunction.instructionList.add(new Instruction(InstructionType.mulF));
                else
                    symbolFunction.instructionList.add(new Instruction(InstructionType.divF));
                break;
            case UINT:
                if(oper.getTokenType() == TokenType.MUL)
                    symbolFunction.instructionList.add(new Instruction(InstructionType.mulI));
                else
                    symbolFunction.instructionList.add(new Instruction(InstructionType.divI));
                break;
            default:
                throw new AnalyzeError(ErrorCode.TypeMismatch, oper.getStartPos());
            }

        }
        return left;
    }

    private Type analyseExpr_e() throws CompileError {
        
        Type left = analyseExpr_f();
        Token trans;

        if(left == Type.NONE)
            throw new AnalyzeError(ErrorCode.TypeMismatch, peek().getStartPos());
        
        while (peek().getTokenType() == TokenType.AS_KW) {
            next();
            trans = peek();
            switch(trans.getDataType()){
                case UINT:
                    if(left == Type.DOUBLE)
                        symbolFunction.instructionList.add(new Instruction(InstructionType.ftoi));
                        left = Type.UINT;
                    break;
                case DOUBLE:
                    if(left == Type.UINT)
                        symbolFunction.instructionList.add(new Instruction(InstructionType.itof));
                        left = Type.DOUBLE;
                    break;
                default:
                    throw new AnalyzeError(ErrorCode.ExpectedToken, trans.getStartPos());
            }

            next();
        }
        return left;
    }

    private Type analyseExpr_f() throws CompileError {
        Type right;
        boolean flag = false;
        Token oper = peek();
        while (peek().getTokenType() == TokenType.MINUS) {
            next();
            flag = !flag;
        }

        right = analyseExpr_g();
        if(flag){
            switch(right){
                
                case UINT:
                    symbolFunction.instructionList.add(new Instruction(InstructionType.negI));
                    break;
                case DOUBLE:
                    symbolFunction.instructionList.add(new Instruction(InstructionType.negF));
                    break;
                default:
                    throw new AnalyzeError(ErrorCode.TypeMismatch, oper.getStartPos());
            }
        }   
        return right;
    }

    private Type analyseExpr_g() throws CompileError {
        
        Token token = peek();
        switch (peek().getTokenType()) {
            case UINT_LITERAL:
                next();
                long int64 = (long)(token.getValue());
                symbolFunction.instructionList.add(new Instruction(InstructionType.pushI, false, int64));
                return Type.UINT;
            case STRING_LITERAL:
                next();
                SymbolVar string = (SymbolVar)symbolTableList.getStringSymbolTable().get(token.getValueString());
                if(string == null){
                    string = new SymbolVar(symbolTableList.getStringSymbolTable().size(), -1 ,-1);
                    string.setIndex_global(symbolTableList.getStringSymbolTable().size());
                    symbolTableList.getStringSymbolTable().put(token.getValueString(), string);
                }
                Instruction ins = new Instruction(InstructionType.pushI, false, (long)(string.getIndex_global()));
                ins.setNeedRelocation(true);
                symbolFunction.instructionList.add(ins);
                
                return Type.UINT;
            case DOUBLE_LITERAL:
                next();
                double double64 = (double)(token.getValue());
                symbolFunction.instructionList.add(new Instruction(InstructionType.pushF, double64));
                return Type.DOUBLE;
            case CHAR_LITERAL:
                next();
                int ch = ((int)(char)token.getValue()) ;
                symbolFunction.instructionList.add(new Instruction(InstructionType.pushI, false, (long)ch));
                return Type.UINT;
            case L_PAREN:
                next();
                Type type = analyseExpr_b();
                expect(TokenType.R_PAREN);
                return type;
            case IDENT:
                Token ident = next();
                if (peek().getTokenType() == TokenType.L_PAREN) {
                    next();
                    SymbolFunction fn = symbolTableList.searchSymbolFn(ident.getValueString());
                    if(fn == null)
                        throw new AnalyzeError(ErrorCode.NotDeclared, ident.getStartPos());
                    if(fn.getIndex_global() == 0)
                        throw new AnalyzeError(ErrorCode.InvalidCall, ident.getStartPos());

                    symbolFunction.instructionList.add(new Instruction(InstructionType.stackAlloc, true, fn.getRet_slot()));

                    boolean flag = false;
                    for (Map.Entry<String, Symbol> entry: symbolTableList.getLocalSymbolTable(fn).getSymbolTable().entrySet()){
                        SymbolVar var = (SymbolVar)entry.getValue();
                        
                        if(!var.isParam())break;
                        if(flag)
                            expect(TokenType.COMMA);
                        flag = true;
                        if((var.getDataType()) != analyseExpr_b())
                            throw new AnalyzeError(ErrorCode.InvalidParam, peek().getStartPos());
                    }
                    expect(TokenType.R_PAREN);
                    if(fn.getIndex_global() <=8){
                        ins = new Instruction(InstructionType.callName, true, fn.getIndex_global());
                        ins.setNeedRelocation(true);
                        symbolFunction.instructionList.add(ins);
                    }
                        
                    else
                        symbolFunction.instructionList.add(new Instruction(InstructionType.call, true, fn.getIndex_global()));
                    return fn.getRetType();
                }
                else{
                    Symbol sym = symbolTableList.searchSymbol(ident.getValueString());
                    if(sym == null || sym instanceof SymbolFunction)
                        throw new AnalyzeError(ErrorCode.NotDeclared, ident.getStartPos());
                        
                    SymbolVar var = (SymbolVar)sym;

                    if(var.isGlobal())
                        symbolFunction.instructionList.add(new Instruction(InstructionType.globA, true, var.getIndex_global()));
                    else if(!var.isParam())
                        symbolFunction.instructionList.add(new Instruction(InstructionType.locA, true, var.getIndex_local()));
                    else
                        symbolFunction.instructionList.add(new Instruction(InstructionType.argA, true, var.getIndex_param() +
                        (symbolFunction.getRetType() == Type.VOID ? 0 : 1) ));
                    
                    symbolFunction.instructionList.add(new Instruction(InstructionType.load64));
                    return var.getDataType();
                }
            default:
                throw new AnalyzeError(ErrorCode.ExpectedToken, token.getStartPos());
        }
    }

/*    private Type analyseAs_expr() throws CompileError{
        expect(TokenType.AS_KW);
        // type
        if(peek().getTokenType() != TokenType.IDENT){
            expect(TokenType.IDENT);
        }
        var type = next();
        if(type.getValueString().equals("int")){
            return Type.INT;
        }
        else if(type.getValueString().equals("double")){
            return Type.DOUBLE;
        }
        else{
            throw new AnalyzeError(ErrorCode.TypeError, type.getStartPos());
        }
    }*/

/*    private void analyseCall_param_list() throws CompileError{
        analyseExpr();
        while(nextIf(TokenType.COMMA) != null){
            analyseExpr();
        }
    }*/

	public void setTokenizer(Tokenizer tokenizer) {
		this.tokenizer = tokenizer;
	}

/*    public static void main(String[] args) throws CompileError, FileNotFoundException {

        var inputFileName = "C:\\Users\\LYYRE-OAO\\Desktop\\test2.c0";
        InputStream input;
        input = new FileInputStream(inputFileName);
        Scanner scanner;
        scanner = new Scanner(input);
        var iter = new StringIter(scanner);
        var tokenizer = new Tokenizer(iter);
        // tokenize
        var analyzer = new Analyser(tokenizer);
        analyzer.analyseProgram();
        System.out.println("The test is finished!");
    }*/

}