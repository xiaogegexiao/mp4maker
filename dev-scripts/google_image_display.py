from bs4 import BeautifulSoup
import requests
import re
import urllib.request
import os
import http.cookiejar
import json

def get_soup(url,header):
    return BeautifulSoup(urllib.request.urlopen(urllib.request.Request(url,headers=header)),'html.parser')


query = input("Please input your image search keyword\n")# you can change the query for the image  here
image_type="ActiOn"
query= query.split()
query='+'.join(query)
url="https://www.google.com.au/search?q="+query+"&source=lnms&tbm=isch&source=lnt"
print(url)
#add the directory for your image here
DIR="Pictures"
header={'User-Agent':"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.134 Safari/537.36"
}
soup = get_soup(url,header)


ActualImages=[]# contains the link for Large original images, type of  image
for a in soup.find_all("div",{"class":"rg_meta"}):
    link , Type =json.loads(a.text)["ou"]  ,json.loads(a.text)["ity"]
    ActualImages.append((link,Type))

print("there are total" , len(ActualImages),"images")

###print images
for i , (img , Type) in enumerate( ActualImages):
    print('image:', i, ' link:', img)
