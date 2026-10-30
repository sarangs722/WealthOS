package com.wealthos.imports

/** Opt-in read-only developer check. No attachments are discovered or used by the automated tests. */
object LocalStatementCheck {
    @JvmStatic fun main(args:Array<String>) {
        require(args.isNotEmpty()) {"Pass explicitly authorized local statement paths."}
        for(path in args) {
            try {
                val parsed=java.io.File(path).inputStream().use {stream->val bytes=HdfcStatements.read(stream);try{HdfcStatements.parse(bytes)}finally{bytes.fill(0)}}
                println("Validated ${parsed.kind.name}; decimal/date and template checks passed.")
            }catch(error:Throwable){
                val lines=error.stackTrace.filter {it.className.startsWith("com.wealthos.imports.")}.map {it.lineNumber}
                System.err.println("Statement validation failed: ${error.javaClass.simpleName}; reader lines $lines; no contents printed.")
                kotlin.system.exitProcess(1)
            }
        }
    }
}
