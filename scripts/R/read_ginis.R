read_ginis <- function(res_path){
  files <- list.files(path=res_path, pattern=".gini")
  lapply(files, function(x) {
    ginis <- round(read.table(x), digits = 4)
    return(ginis)
  })
}