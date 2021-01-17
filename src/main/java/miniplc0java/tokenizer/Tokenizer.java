package miniplc0java.tokenizer;

import miniplc0java.error.CompileError;
import miniplc0java.error.ErrorCode;
import miniplc0java.error.TokenizeError;
import miniplc0java.util.Pos;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;

public class Tokenizer {

    private StringIter it;

    public Tokenizer(StringIter it) {
        this.it = it;
    }

    // 这里本来是想实现 Iterator<Token> 的，但是 Iterator 不允许抛异常，于是就这样了
    /**
     * 获取下一个 Token
     * 
     * @return
     * @throws TokenizeError 如果解析有异常则抛出
     */
    public Token nextToken() throws TokenizeError {
        it.readAll();

        // 跳过之前的所有空白字符
        skipSpaceCharacters();

        if (it.isEOF()) {
            return new Token(TokenType.EOF, "", it.currentPos(), it.currentPos());
        }

        char peek = it.peekChar();
        if (Character.isDigit(peek)) {
            return lexUINTOrDOUBLE();
        } else if (peek == '"'){
            return lexSTRINGorUnknwon();
        } else if (Character.isLetter(peek) || peek == '_') {
            return lexIDENTOrKW();
        } else if (peek == '/'){
            it.nextChar();
            if (it.peekChar() == '/') {
                while(it.nextChar() != '\n'){}
                return nextToken();
            }
            else{
                throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
            }
        } else {
            return lexOperatorOrUnknown();
        }
    }
    private Token lexUINTOrDOUBLE() throws TokenizeError {
        // 请填空：
        // 直到查看下一个字符不是数字为止:
        char c;
        StringBuilder token = new StringBuilder();
        Pos startPos = it.currentPos();
        while(Character.isDigit(c = it.peekChar())){
            c = it.nextChar();
            token.append(c);
        }
        if(c == '.'){
            c = it.nextChar();
            token.append(c);
            if(!Character.isDigit(c = it.peekChar())){
                throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
            }
            while(Character.isDigit(c = it.peekChar())){
                c = it.nextChar();
                token.append(c);
            }
            if(c == 'e' || c == 'E'){
                it.nextChar();
                token.append(c);
                c = it.peekChar();
                if(c == '+' || c == '-'){
                    it.nextChar();
                    token.append(c);
                }
                if(!Character.isDigit(c = it.peekChar())){
                    throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
                }
                while(Character.isDigit(c = it.peekChar())){
                    c = it.nextChar();
                    token.append(c);
                }
            }
            double doublelit = Double.parseDouble(token.toString());
            return new Token(TokenType.DOUBLE_LITERAL, doublelit, startPos, it.currentPos());
        }
        else{
            int uint = Integer.parseInt(token.toString());
            return new Token(TokenType.UINT_LITERAL, uint, startPos, it.currentPos());
        }
        // -- 前进一个字符，并存储这个字符
        //

        // 解析存储的字符串为无符号整数
        // 解析成功则返回无符号整数类型的token，否则返回编译错误
        //
        // Token 的 Value 应填写数字的值
    }
    private Token lexSTRINGorUnknwon() throws TokenizeError {
        char c;
        it.nextChar();
        StringBuilder token = new StringBuilder();
        Pos startPos = it.currentPos();
        while((c = it.peekChar()) != '"'){
            if(c != '\\'){
                c = it.nextChar();
                token.append(c);
            }
            else{
                c = it.nextChar();
                token.append(c);
                c = it.peekChar();
                switch(c){
                    case '\\':
                    case '"':
                    case '\'':
                    case 'n':
                    case 't':
                    case 'r':
                        c = it.nextChar();
                        token.append(c);
                        break;
                    default:
                        throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
                }
            }
            if(it.isEOF()){
                throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
            }
        }
        it.nextChar();
        return new Token(TokenType.STRING_LITERAL, token, startPos, it.currentPos());
    }
    private Token lexIDENTOrKW() throws TokenizeError {
        // 请填空：
        // 直到查看下一个字符不是数字或字母为止:
        // -- 前进一个字符，并存储这个字符
        //
        char c;
        StringBuilder token = new StringBuilder();
        Pos startPos = it.currentPos();
        while(Character.isDigit(c = it.peekChar()) || Character.isAlphabetic(c = it.peekChar()) ||
                (c = it.peekChar()) == '_'){
            c = it.nextChar();
            token.append(c);
        }
        // 尝试将存储的字符串解释为关键字
        // -- 如果是关键字，则返回关键字类型的 token
        // -- 否则，返回标识符
        //
        // Token 的 Value 应填写标识符或关键字的字符串
        switch (token.toString()) {
            case "fn":
                return new Token(TokenType.FN_KW, token.toString(), startPos, it.currentPos());
            case "let":
                return new Token(TokenType.LET_KW, token.toString(), startPos, it.currentPos());
            case "const":
                return new Token(TokenType.CONST_KW, token.toString(), startPos, it.currentPos());
            case "as":
                return new Token(TokenType.AS_KW, token.toString(), startPos, it.currentPos());
            case "while":
                return new Token(TokenType.WHILE_KW, token.toString(), startPos, it.currentPos());
            case "if":
                return new Token(TokenType.IF_KW, token.toString(), startPos, it.currentPos());
            case "else":
                return new Token(TokenType.ELSE_KW, token.toString(), startPos, it.currentPos());
            case "return":
                return new Token(TokenType.RETURN_KW, token.toString(), startPos, it.currentPos());
            case "break":
                return new Token(TokenType.BREAK_KW, token.toString(), startPos, it.currentPos());
            case "continue":
                return new Token(TokenType.CONTINUE_KW, token.toString(), startPos, it.currentPos());
            default:
                return new Token(TokenType.IDENT, token.toString(), startPos, it.currentPos());
        }
    }

    private Token lexOperatorOrUnknown() throws TokenizeError {
        switch (it.nextChar()) {
            case '+':
                return new Token(TokenType.PLUS, '+', it.previousPos(), it.currentPos());

            case '-':
                // 填入返回语句
                if(it.peekChar() == '>'){
                    it.nextChar();
                    return new Token(TokenType.ARROW, "->", it.previousPos(), it.currentPos());
                }
                return new Token(TokenType.MINUS, '-', it.previousPos(), it.currentPos());
            case '*':
                // 填入返回语句
                return new Token(TokenType.MUL, '*', it.previousPos(), it.currentPos());

            case '/':
                // 填入返回语句
                return new Token(TokenType.DIV, '/', it.previousPos(), it.currentPos());
            // 填入更多状态和返回语句
            case '=':
                if((it.peekChar() == '=')){
                    it.nextChar();
                    return new Token(TokenType.EQ, "==", it.previousPos(), it.currentPos());
                }
                return new Token(TokenType.ASSIGN, '=', it.previousPos(), it.currentPos());
            case '!':
                if(it.peekChar() == '='){
                    it.nextChar();
                    return new Token(TokenType.NEQ, "==", it.previousPos(), it.currentPos());
                }
                throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
            case '<':
                if(it.peekChar() == '='){
                    it.nextChar();
                    return new Token(TokenType.LE, "<=", it.previousPos(), it.currentPos());
                }
                return new Token(TokenType.LT, '<', it.previousPos(), it.currentPos());
            case '>':
                if(it.peekChar() == '='){
                    it.nextChar();
                    return new Token(TokenType.GE, ">=", it.previousPos(), it.currentPos());
                }
                return new Token(TokenType.GT, '>', it.previousPos(), it.currentPos());
            case '(':
                return new Token(TokenType.L_PAREN, '(', it.previousPos(), it.currentPos());
            case ')':
                return new Token(TokenType.R_PAREN, ')', it.previousPos(), it.currentPos());
            case '{':
                return new Token(TokenType.L_BRACE, '{', it.previousPos(), it.currentPos());
            case '}':
                return new Token(TokenType.R_BRACE, '}', it.previousPos(), it.currentPos());
            case ',':
                return new Token(TokenType.COMMA, ',', it.previousPos(), it.currentPos());
            case ':':
                return new Token(TokenType.COLON, ':', it.previousPos(), it.currentPos());
            case ';':
                return new Token(TokenType.SEMICOLON, ';', it.previousPos(), it.currentPos());
            default:
                // 不认识这个输入，摸了
                throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
        }
    }

    private void skipSpaceCharacters() {
        while (!it.isEOF() && Character.isWhitespace(it.peekChar())) {
            it.nextChar();
        }
    }
/*    public static void main(String[] args) throws CompileError, FileNotFoundException {
        var inputFileName = "C:\\Users\\LYYRE-OAO\\Desktop\\test2.c0";
        InputStream input;
        input = new FileInputStream(inputFileName);
        PrintStream output;
        output = System.out;
        Scanner scanner;
        scanner = new Scanner(input);
        var iter = new StringIter(scanner);
        var tokenizer = new Tokenizer(iter);
        // tokenize
        var tokens = new ArrayList<Token>();
        try {
            while (true) {
                var token = tokenizer.nextToken();
                if (token.getTokenType().equals(TokenType.EOF)) {
                    break;
                }
                tokens.add(token);
            }
        } catch (Exception e) {
            // 遇到错误不输出，直接退出
            System.err.println(e);
            System.exit(0);
            return;
        }
        for (Token token : tokens) {
            output.println(token.toString());
        }
    }*/


}
